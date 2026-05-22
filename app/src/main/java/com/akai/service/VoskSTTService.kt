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
        isModelLoading = true
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
        recognizer?.close()
        voskModel?.close()

        val modelFolder = when (language) {
            Language.FILIPINO -> "vosk-model-tl"
            Language.ENGLISH -> "vosk-model-en"
        }

        val modelPath = copyModelToCache(modelFolder)
        voskModel   = Model(modelPath)
        recognizer  = Recognizer(voskModel, SAMPLE_RATE.toFloat())
        Log.d(TAG, "Loaded model: $modelFolder")
    }

    private fun copyModelToCache(modelFolder: String): String {
        val cacheDir = File(context.cacheDir, modelFolder)
        if (cacheDir.exists() && cacheDir.list()?.isNotEmpty() == true) {
            Log.d(TAG, "Model already in cache: $modelFolder")
            return cacheDir.absolutePath
        }

        cacheDir.mkdirs()
        copyAssetFolder(modelFolder, cacheDir)
        Log.d(TAG, "Model copied to cache: $modelFolder")
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
            }
        }
    }

    fun switchLanguage(language: Language) {
        if (isRecording || isModelLoading) return  // Prevent switch during load or recording
        currentLanguage = language
        loadModel()
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
        recognizer?.close()
        voskModel?.close()
        scope.cancel()
    }
}
