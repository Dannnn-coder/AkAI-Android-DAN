package com.akai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.framework.image.BitmapImageBuilder
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.ArrayDeque

class FSLRecognitionService(private val context: Context) {

    private val TAG = "FSLRecognitionService"

    // Model config
    private val MODEL_FILE       = "akai_model.tflite"
    private val ACTIONS_FILE     = "actions.txt"
    private val SEQ_LENGTH_FILE  = "sequence_length.txt"
    private val FEATURE_DIM      = 138
    private val CONFIDENCE_THRESHOLD = 0.55f
    private val STABLE_REQUIRED  = 2
    private val PREDICT_EVERY_N  = 3
    private val MIN_FRAMES_PCT   = 0.15f

    private var interpreter: Interpreter? = null
    private var actions: List<String> = emptyList()
    private var sequenceLength: Int = 120

    // Buffers
    private val frameBuffer    = ArrayDeque<FloatArray>()
    private val predHistory    = ArrayDeque<String>()
    private var frameCount     = 0
    private var lastOutput     = ""
    private var lastOutputTime = 0L
    private val COOLDOWN_MS    = 800L

    // MediaPipe
    private var handLandmarker: HandLandmarker? = null

    // Callback
    var onGestureRecognized: ((String) -> Unit)? = null

    fun loadModel() {
        try {
            // Load sequence length from text file
            sequenceLength = context.assets.open(SEQ_LENGTH_FILE)
                .bufferedReader().readText().trim().toInt()
            Log.d(TAG, "Sequence length: $sequenceLength")

            // Load actions from text file (one label per line)
            actions = context.assets.open(ACTIONS_FILE)
                .bufferedReader().readLines().filter { it.isNotBlank() }
            Log.d(TAG, "Actions loaded: ${actions.size} gestures — $actions")

            // Load TFLite model
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply { setNumThreads(2) }
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "TFLite model loaded")

            // Init MediaPipe HandLandmarker
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()
            val handOptions = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setRunningMode(RunningMode.IMAGE)
                .build()
            handLandmarker = HandLandmarker.createFromOptions(context, handOptions)
            Log.d(TAG, "MediaPipe HandLandmarker ready")

        } catch (e: UnsatisfiedLinkError) {
            // MediaPipe native library not supported on x86_64 emulators
            // App will run but gesture recognition requires a physical ARM device
            Log.w(TAG, "MediaPipe not available on this device/emulator: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed: ${e.message}")
        }
    }

    fun processFrame(imageProxy: ImageProxy) {
        try {
            // Convert to ARGB_8888 — required by MediaPipe
            val rawBitmap = imageProxy.toBitmap()
            val bitmap = if (rawBitmap.config != Bitmap.Config.ARGB_8888) {
                rawBitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                rawBitmap
            }

            // Apply rotation from camera metadata
            val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()
            val rotated  = rotateBitmap(bitmap, rotation)
            val mpImage  = BitmapImageBuilder(rotated).build()
            val result   = handLandmarker?.detect(mpImage)

            Log.d(TAG, "Frame processed — rotation=$rotation hands=${result?.landmarks()?.size ?: 0}")

            val keypoints = extractKeypoints(result)

            if (keypoints != null) {
                frameBuffer.addLast(keypoints)
                if (frameBuffer.size > sequenceLength) frameBuffer.removeFirst()
                frameCount++

                val minFrames = (sequenceLength * MIN_FRAMES_PCT).toInt()
                if (frameCount % PREDICT_EVERY_N == 0 && frameBuffer.size >= minFrames) {
                    runInference()
                }
            } else {
                // No hands — clear buffer
                frameBuffer.clear()
                predHistory.clear()
                frameCount = 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun runInference() {
        val padded = padSequence()
        val input  = Array(1) { Array(sequenceLength) { FloatArray(FEATURE_DIM) } }
        for (t in 0 until sequenceLength) {
            for (f in 0 until FEATURE_DIM) {
                input[0][t][f] = padded[t * FEATURE_DIM + f]
            }
        }

        val output = Array(1) { FloatArray(actions.size) }
        interpreter?.run(input, output)

        val probs      = output[0]
        val maxIdx     = probs.indices.maxByOrNull { probs[it] } ?: return
        val confidence = probs[maxIdx]
        val predicted  = actions[maxIdx]

        if (predHistory.size >= 6) predHistory.removeFirst()
        predHistory.addLast(predicted)

        val voted  = predHistory.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return
        val stable = predHistory.count { it == voted }
        val now    = System.currentTimeMillis()

        if (confidence >= CONFIDENCE_THRESHOLD
            && stable >= STABLE_REQUIRED
            && voted != lastOutput
            && (now - lastOutputTime) > COOLDOWN_MS) {
            lastOutput     = voted
            lastOutputTime = now
            Log.d(TAG, "DETECTED: $voted (conf: $confidence)")
            onGestureRecognized?.invoke(voted)
        }
    }

    private fun extractKeypoints(result: HandLandmarkerResult?): FloatArray? {
        if (result == null || result.landmarks().isEmpty()) return null

        val lhRaw   = FloatArray(63)
        val rhRaw   = FloatArray(63)
        val poseRaw = FloatArray(12)

        val handedness = result.handedness()
        val landmarks  = result.landmarks()

        for (i in landmarks.indices) {
            val lms   = landmarks[i]
            val label = if (i < handedness.size) handedness[i][0].categoryName() else "Right"

            val wristX = lms[0].x()
            val wristY = lms[0].y()
            val wristZ = lms[0].z()

            if (label == "Left") {
                for (j in lms.indices) {
                    lhRaw[j * 3]     = lms[j].x() - wristX
                    lhRaw[j * 3 + 1] = lms[j].y() - wristY
                    lhRaw[j * 3 + 2] = lms[j].z() - wristZ
                }
            } else {
                for (j in lms.indices) {
                    rhRaw[j * 3]     = lms[j].x() - wristX
                    rhRaw[j * 3 + 1] = lms[j].y() - wristY
                    rhRaw[j * 3 + 2] = lms[j].z() - wristZ
                }
            }
        }

        return lhRaw + rhRaw + poseRaw
    }

    private fun padSequence(): FloatArray {
        val flat = FloatArray(sequenceLength * FEATURE_DIM)
        val buf  = frameBuffer.toList()
        val n    = buf.size

        if (n == 0) return flat

        val padCount = sequenceLength - n
        val first    = buf[0]

        // Pad start with first frame repeat — matches train_model.py
        for (t in 0 until padCount) {
            first.copyInto(flat, t * FEATURE_DIM)
        }
        for (t in 0 until n) {
            buf[t].copyInto(flat, (padCount + t) * FEATURE_DIM)
        }
        return flat
    }

    fun clearBuffer() {
        frameBuffer.clear()
        predHistory.clear()
        frameCount = 0
        lastOutput = ""
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        val stream = FileInputStream(fd.fileDescriptor)
        return stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }


    fun close() {
        interpreter?.close()
        handLandmarker?.close()
    }
}

operator fun FloatArray.plus(other: FloatArray): FloatArray {
    val result = FloatArray(this.size + other.size)
    this.copyInto(result, 0)
    other.copyInto(result, this.size)
    return result
}
