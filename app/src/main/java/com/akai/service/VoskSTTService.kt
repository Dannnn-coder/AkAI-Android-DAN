package com.akai.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

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

    private var voskModel: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var currentLanguage = Language.FILIPINO
    private var loadedLanguage: Language? = null  // tracks what's actually in memory

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onTranscriptionResult: ((String) -> Unit)? = null
    var onRecordingStateChanged: ((RecordingState) -> Unit)? = null
    var onModelReady: (() -> Unit)? = null

    enum class RecordingState { DORMANT, RECORDING, PROCESSING }
    enum class Language { FILIPINO, ENGLISH }

    var isModelLoading = false
        private set

    fun loadModel() {
        if (isModelLoading) return  // Prevent concurrent loads
        // Only skip reload if the CORRECT language is already in memory
        if (voskModel != null && recognizer != null && loadedLanguage == currentLanguage) {
            onModelReady?.invoke()
            return
        }
        isModelLoading = true  // Lock toggle until load completes
        scope.launch {
            try {
                loadLanguageModel(currentLanguage)
                Log.d(TAG, "Vosk model loaded: $currentLanguage")
                withContext(Dispatchers.Main) {
                    isModelLoading = false
                    onModelReady?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Vosk model: ${e.message}")
                withContext(Dispatchers.Main) { isModelLoading = false }
            }
        }
    }

    private fun loadLanguageModel(language: Language) {
        // Null out before closing — prevents stale non-null references
        val oldRecognizer = recognizer
        val oldModel      = voskModel
        recognizer    = null
        voskModel     = null
        loadedLanguage = null  // clear until new model fully loads
        oldRecognizer?.close()
        oldModel?.close()

        val modelFolder = when (language) {
            Language.FILIPINO -> "vosk-model-tl"
            Language.ENGLISH -> "vosk-model-en"
        }

        val modelPath  = copyModelToCache(modelFolder)
        voskModel      = Model(modelPath)
        recognizer     = Recognizer(voskModel, SAMPLE_RATE.toFloat())
        loadedLanguage = language  // only mark loaded AFTER success
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
     * Preloads a specific language model silently in the background.
     * Called during splash screen — no UI callbacks triggered.
     * Safe to call from a coroutine on Dispatchers.IO.
     */
    fun preloadModel(language: Language) {
        try {
            loadLanguageModel(language)
            Log.d(TAG, "Preloaded model: $language")
        } catch (e: Exception) {
            Log.e(TAG, "Preload failed for $language: ${e.message}")
        }
    }

    /**
     * Switch STT language. Returns true if the switch was accepted and a load kicked off,
     * false if it was rejected (busy recording or already loading). Callers should only
     * show a "Loading…" state when this returns true — otherwise the UI would be stuck
     * showing "Loading" for a load that never started.
     */
    fun switchLanguage(language: Language): Boolean {
        if (isRecording || isModelLoading) return false  // busy — don't accept the switch
        if (language == loadedLanguage && voskModel != null) {
            // Already on this language and loaded — nothing to do, tell UI it's ready.
            currentLanguage = language
            onModelReady?.invoke()
            return true
        }
        currentLanguage = language
        loadModel()
        return true
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording || recognizer == null) return
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

                val buffer = ShortArray(bufferSize)
                recognizer?.reset()

                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        recognizer?.acceptWaveForm(buffer, read)
                    }
                }

                // Get final result
                val resultJson = recognizer?.finalResult ?: "{}"
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

    fun getCurrentLanguage() = currentLanguage

    fun close() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recognizer?.close()
        recognizer = null
        voskModel?.close()
        voskModel = null
        loadedLanguage = null
        scope.cancel()
    }
}
