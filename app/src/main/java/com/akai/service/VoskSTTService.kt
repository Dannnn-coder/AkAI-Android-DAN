package com.akai.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * WhisperSTTService — Offline Speech-to-Text using Vosk
 * Supports Filipino (Tagalog) and English
 * Fully offline — no internet required
 */
class VoskSTTService(private val context: Context) {

    private val TAG = "WhisperSTTService"

    private val SAMPLE_RATE    = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT
    // Read the mic in ~40ms slices so the live waveform gets ~25 updates/sec.
    // Vosk is still fed the exact same continuous PCM stream, just in pieces.
    private val AMPLITUDE_CHUNK = 640

    /** One resident model + recognizer for a language, kept alongside the others. */
    private class LoadedModel(val model: Model, val recognizer: Recognizer)

    // Models are cached PER LANGUAGE and kept resident side by side. English is the
    // default voice language; the heavy ~776MB Filipino model is preloaded into RAM
    // at startup so switching to it is instant. Holding both costs ~850MB native,
    // which is why loads are serialized below — never two large copies at once.
    private val loadedModels = ConcurrentHashMap<Language, LoadedModel>()
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var currentLanguage = Language.ENGLISH

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // Serializes model loads so two languages never copy/parse concurrently.
    private val loadMutex = Mutex()
    // Languages whose load is queued or running (dedupes repeat requests).
    private val loadingLanguages = ConcurrentHashMap.newKeySet<Language>()
    // Languages that still owe an onModelReady callback once their load finishes,
    // so a silent preload can be "upgraded" by a real load request for the same one.
    private val pendingReadyNotify = ConcurrentHashMap.newKeySet<Language>()
    private val mainHandler = Handler(Looper.getMainLooper())
    // Set once the service is torn down; lets an in-flight native load that cannot be
    // cancelled avoid registering itself into the (already-cleared) model map.
    @Volatile private var closed = false

    var onTranscriptionResult: ((String) -> Unit)? = null
    var onRecordingStateChanged: ((RecordingState) -> Unit)? = null
    var onModelReady: (() -> Unit)? = null
    // Real microphone RMS amplitude (0..1 smoothed). Fired while recording only.
    var onLevel: ((Float) -> Unit)? = null

    private var lastLevel = 0f

    enum class RecordingState { DORMANT, RECORDING, PROCESSING }
    enum class Language { FILIPINO, ENGLISH }

    var isModelLoading = false
        private set

    /** Loads the CURRENT language model if it isn't already resident. */
    fun loadModel() {
        if (loadedModels.containsKey(currentLanguage)) {
            onModelReady?.invoke()
            return
        }
        requestLoad(currentLanguage, notifyReady = true)
    }

    /** Enqueue a background load of [language] (deduped, serialized). [notifyReady]
     *  means [onModelReady] should fire once this language is usable. */
    private fun requestLoad(language: Language, notifyReady: Boolean) {
        // Already in memory — nothing to do but report readiness if asked.
        if (loadedModels.containsKey(language)) {
            if (notifyReady) invokeOnMain { onModelReady?.invoke() }
            return
        }
        if (notifyReady) pendingReadyNotify.add(language)
        // A load for this language is already queued/running — let it finish.
        if (!loadingLanguages.add(language)) return
        setModelLoading()
        scope.launch {
            loadMutex.withLock {
                try {
                    if (!loadedModels.containsKey(language)) {
                        loadLanguageModel(language)
                    }
                    if (pendingReadyNotify.remove(language)) {
                        invokeOnMain { onModelReady?.invoke() }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to load Vosk model $language: ${e.javaClass.simpleName}: ${e.message}", e)
                    pendingReadyNotify.remove(language)
                } finally {
                    loadingLanguages.remove(language)
                    setModelLoading()
                }
            }
        }
    }

    /** Publishes [isModelLoading] = "any load queued/running" onto the main thread. */
    private fun setModelLoading() {
        invokeOnMain { isModelLoading = loadingLanguages.isNotEmpty() }
    }

    private fun invokeOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /** Loads [language] into memory and caches it WITHOUT touching any other language. */
    private fun loadLanguageModel(language: Language) {
        val modelFolder = when (language) {
            Language.FILIPINO -> "vosk-model-tl"
            Language.ENGLISH -> "vosk-model-en"
        }
        val modelPath = copyModelToCache(modelFolder)
        val model = Model(modelPath)
        val recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
        // The service may have been closed while this native load was running
        // (it cannot be cancelled); release instead of caching a stale model.
        if (closed) {
            recognizer.close()
            model.close()
            return
        }
        loadedModels[language] = LoadedModel(model, recognizer)
        Log.d(TAG, "Loaded model: $modelFolder")
    }

    private fun copyModelToCache(modelFolder: String): String {
        val cacheDir = File(context.cacheDir, modelFolder)
        // A ".done" marker is written ONLY after a full, successful copy. We treat the
        // cache as valid only if that marker exists — this prevents loading a half-copied,
        // corrupt model (which happens if a previous copy was interrupted, e.g. the app was
        // backgrounded mid-copy of the ~776MB Tagalog model). Without this, Vosk silently
        // hangs forever on "Loading speech model".
        val doneMarker = File(context.cacheDir, "$modelFolder.done")
        if (doneMarker.exists() && cacheDir.exists() && cacheDir.list()?.isNotEmpty() == true) {
            Log.d(TAG, "Model already fully in cache: $modelFolder")
            return cacheDir.absolutePath
        }

        // Cache is missing OR was left incomplete → wipe any partial copy and redo it cleanly.
        Log.d(TAG, "Copying model to cache (fresh): $modelFolder")
        if (cacheDir.exists()) cacheDir.deleteRecursively()
        doneMarker.delete()

        // Copy into a temp dir first, then atomically rename to the final name. The final
        // folder therefore only ever exists in a COMPLETE state.
        val tmpDir = File(context.cacheDir, "$modelFolder.tmp")
        if (tmpDir.exists()) tmpDir.deleteRecursively()
        tmpDir.mkdirs()

        val startMs = System.currentTimeMillis()
        copyAssetFolder(modelFolder, tmpDir)

        if (!tmpDir.renameTo(cacheDir)) {
            // Fallback if rename across the same dir fails for any reason.
            tmpDir.copyRecursively(cacheDir, overwrite = true)
            tmpDir.deleteRecursively()
        }
        doneMarker.createNewFile()  // mark the copy as complete
        val secs = (System.currentTimeMillis() - startMs) / 1000.0
        Log.d(TAG, "Model copied to cache: $modelFolder in ${secs}s")
        return cacheDir.absolutePath
    }

    private fun copyAssetFolder(assetPath: String, destDir: File) {
        val assets = context.assets.list(assetPath) ?: return
        for (asset in assets) {
            val subAsset = "$assetPath/$asset"
            val destFile = File(destDir, asset)
            val subList  = context.assets.list(subAsset)
            if (subList != null && subList.isNotEmpty()) {
                destFile.mkdirs()
                copyAssetFolder(subAsset, destFile)
            } else {
                context.assets.open(subAsset).use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                // Log large files so a stalled copy is visible in logcat (e.g. HCLG.fst ~670MB).
                if (destFile.length() > 50_000_000L) {
                    Log.d(TAG, "  copied large asset: $asset (${destFile.length() / 1_000_000}MB)")
                }
            }
        }
    }

    /**
     * Preloads [language] into memory in the background WITHOUT changing the current
     * language and without firing [onModelReady]. Called at main-screen startup to warm
     * the heavy Filipino model so a later switch to it is instant. Safe to call
     * repeatedly — an already-loaded (or in-flight) language is left alone.
     *
     * Runs once MainActivity's ViewModel exists: the splash and main screens each own
     * a separate ViewModel, so loading during splash would be discarded on transition.
     */
    fun preloadModel(language: Language) {
        if (loadedModels.containsKey(language)) return
        requestLoad(language, notifyReady = false)
    }

    /**
     * Switch STT language. Returns true if the switch was accepted and a load kicked off,
     * false if it was rejected (busy recording or already loading). Callers should only
     * show a "Loading…" state when this returns true — otherwise the UI would be stuck
     * showing "Loading" for a load that never started.
     */
    fun switchLanguage(language: Language): Boolean {
        if (isRecording || isModelLoading) return false  // busy — don't accept the switch
        currentLanguage = language
        if (loadedModels.containsKey(language)) {
            // Already resident (e.g. preloaded) — instantly usable.
            onModelReady?.invoke()
            return true
        }
        requestLoad(language, notifyReady = true)
        return true
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        // Snapshot the recognizer for the CURRENT language; bail if it isn't loaded.
        val activeRecognizer = loadedModels[currentLanguage]?.recognizer ?: return
        if (isRecording) return
        isRecording = true
        onRecordingStateChanged?.invoke(RecordingState.RECORDING)

        scope.launch {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
                )
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
                )
                audioRecord?.startRecording()
                Log.d(TAG, "Recording started")

                val buffer = ShortArray(AMPLITUDE_CHUNK)
                activeRecognizer.reset()
                lastLevel = 0f
                onLevel?.invoke(0f)

                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, AMPLITUDE_CHUNK) ?: 0
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            val s = buffer[i].toDouble()
                            sum += s * s
                        }
                        val rms = sqrt(sum / read)
                        activeRecognizer.acceptWaveForm(buffer, read)
                        publishLevel((rms / Short.MAX_VALUE).toFloat())
                    }
                }

                // Get final result
                val resultJson = activeRecognizer.finalResult
                val text = JSONObject(resultJson).optString("text", "").trim()
                Log.d(TAG, "Final result: $text")

                withContext(Dispatchers.Main) {
                    onRecordingStateChanged?.invoke(RecordingState.PROCESSING)
                    if (text.isNotBlank()) {
                        onTranscriptionResult?.invoke(text)
                    }
                    onRecordingStateChanged?.invoke(RecordingState.DORMANT)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}")
                isRecording = false  // reset flag so startRecording() can be called again
                withContext(Dispatchers.Main) {
                    onRecordingStateChanged?.invoke(RecordingState.DORMANT)
                }
            } finally {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            }
        }
    }

    fun stopRecording() {
        isRecording = false
    }

    /** Smoothed raw RMS (0..1) -> display level. Fast attack, gentler decay so
     *  the waveform does not flicker between reads. Called on the IO thread. */
    private fun publishLevel(raw: Float) {
        lastLevel = if (raw > lastLevel) {
            lastLevel * 0.55f + raw * 0.45f
        } else {
            lastLevel * 0.78f + raw * 0.22f
        }
        // Sensitivity: raw RMS for normal speech is typically ~2-8% of full
        // scale. A modest 3x gain (was 7x) keeps normal speech mid-sized, and
        // the soft knee below means even full-scale input can never slam the
        // display ceiling — the waveform stays comfortably inside its bounds.
        val scaled = lastLevel * AMPLITUDE_SENSITIVITY
        val soft = scaled / (1f + 1.2f * scaled)
        onLevel?.invoke(soft.coerceIn(0f, 1f))
    }

    companion object {
        private const val AMPLITUDE_SENSITIVITY = 3f
    }

    fun getCurrentLanguage() = currentLanguage

    /** True only when [language] is fully loaded and usable right now. */
    fun isLanguageLoaded(language: Language): Boolean =
        loadedModels.containsKey(language)

    fun close() {
        closed = true
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        // Free EVERY resident language (RA 10173-minded: nothing model- or audio-related
        // is left in memory once the owning screen is gone).
        loadedModels.values.forEach {
            it.recognizer.close()
            it.model.close()
        }
        loadedModels.clear()
        loadingLanguages.clear()
        pendingReadyNotify.clear()
        scope.cancel()
    }
}
