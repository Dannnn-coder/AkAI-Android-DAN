package com.akai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var tvPrediction: TextView
    private lateinit var scrollConversation: ScrollView
    private lateinit var conversationContainer: LinearLayout
    private lateinit var btnFSLMode: LinearLayout
    private lateinit var btnSpeechMode: LinearLayout
    private lateinit var tvFSLStatus: TextView
    private lateinit var tvSpeechStatus: TextView

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fslRecognitionService: FSLRecognitionService

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isFSLMode = true

    companion object {
        private const val TAG = "AkAI"
        private const val REQUEST_PERMISSIONS = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraPreview        = findViewById(R.id.cameraPreview)
        tvPrediction         = findViewById(R.id.tvPrediction)
        scrollConversation   = findViewById(R.id.scrollConversation)
        conversationContainer = findViewById(R.id.conversationContainer)
        btnFSLMode           = findViewById(R.id.btnFSLMode)
        btnSpeechMode        = findViewById(R.id.btnSpeechMode)
        tvFSLStatus          = findViewById(R.id.tvFSLStatus)
        tvSpeechStatus       = findViewById(R.id.tvSpeechStatus)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize FSL recognition service
        fslRecognitionService = FSLRecognitionService(this)
        fslRecognitionService.loadModel()

        // Set callback — when a gesture is confirmed, add to conversation thread
        fslRecognitionService.onGestureRecognized = { gesture ->
            runOnUiThread {
                tvPrediction.text = gesture.uppercase()
                addDeafMessage(gesture)
            }
        }

        // Mode switcher
        btnFSLMode.setOnClickListener { switchToFSLMode() }
        btnSpeechMode.setOnClickListener { switchToSpeechMode() }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(cameraPreview.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isFSLMode) {
                            fslRecognitionService.processFrame(imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
                Log.d(TAG, "Camera started")
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed: ${e.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchToFSLMode() {
        isFSLMode = true
        btnFSLMode.setBackgroundColor(ContextCompat.getColor(this, R.color.fsl_active))
        btnSpeechMode.setBackgroundColor(ContextCompat.getColor(this, R.color.speech_inactive))
        tvFSLStatus.text = "FSL Camera — Active"
        tvSpeechStatus.text = "Speech Input — Dormant"
        tvPrediction.text = "Show your hands..."
    }

    private fun switchToSpeechMode() {
        isFSLMode = false
        fslRecognitionService.clearBuffer()
        btnFSLMode.setBackgroundColor(ContextCompat.getColor(this, R.color.fsl_inactive))
        btnSpeechMode.setBackgroundColor(ContextCompat.getColor(this, R.color.speech_active))
        tvFSLStatus.text = "FSL Camera — Dormant"
        tvSpeechStatus.text = "Speech Input — Active"
        tvPrediction.text = "Tap mic to speak..."
    }

    private fun addDeafMessage(text: String) {
        val inflater = LayoutInflater.from(this)
        val bubble = inflater.inflate(R.layout.item_message_deaf, conversationContainer, false)
        bubble.findViewById<TextView>(R.id.tvMessage).text = text
        bubble.findViewById<TextView>(R.id.tvTimestamp).text = getCurrentTime()
        conversationContainer.addView(bubble)
        scrollConversation.post { scrollConversation.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    fun addHearingMessage(text: String) {
        val inflater = LayoutInflater.from(this)
        val bubble = inflater.inflate(R.layout.item_message_hearing, conversationContainer, false)
        bubble.findViewById<TextView>(R.id.tvMessage).text = text
        bubble.findViewById<TextView>(R.id.tvTimestamp).text = getCurrentTime()
        conversationContainer.addView(bubble)
        scrollConversation.post { scrollConversation.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && allPermissionsGranted()) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera and microphone permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        fslRecognitionService.close()
        scope.cancel()
    }
}
