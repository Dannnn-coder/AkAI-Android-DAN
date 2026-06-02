package com.akai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.akai.data.ConversationEntry
import com.akai.ui.ConversationBubbleWidget
import com.akai.viewmodel.ConversationViewModel
import com.akai.service.VoskSTTService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: ConversationViewModel

    // Views
    private lateinit var cameraPreview: PreviewView
    private lateinit var tvPrediction: TextView
    private lateinit var scrollConversation: ScrollView
    private lateinit var conversationContainer: LinearLayout
    private lateinit var btnFSLMode: LinearLayout
    private lateinit var btnSpeechMode: LinearLayout
    private lateinit var tvFSLStatus: TextView
    private lateinit var tvSpeechStatus: TextView
    private lateinit var tvLanguageToggle: TextView

    // Letter buffer
    private lateinit var letterBufferPanel: LinearLayout
    private lateinit var letterChipsContainer: LinearLayout
    private lateinit var btnConfirmWord: TextView
    private lateinit var btnDeleteWord: TextView

    // Top 3
    private lateinit var top3Panel: LinearLayout
    private lateinit var btnChoice1: LinearLayout
    private lateinit var btnChoice2: LinearLayout
    private lateinit var btnChoice3: LinearLayout
    private lateinit var tvChoice1: TextView
    private lateinit var tvChoice2: TextView
    private lateinit var tvChoice3: TextView
    private lateinit var tvConf1: TextView
    private lateinit var tvConf2: TextView
    private lateinit var tvConf3: TextView
    private lateinit var btnDismissTop3: TextView
    private var top3Labels = listOf<String>()

    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val REQUEST_PERMISSIONS = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[ConversationViewModel::class.java]

        bindViews()
        setupTop3Panel()
        setupLetterBuffer()
        setupModeButtons()
        setupFSLCallbacks()
        setupVoskCallbacks()
        observeViewModel()

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS)

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun bindViews() {
        cameraPreview         = findViewById(R.id.cameraPreview)
        tvPrediction          = findViewById(R.id.tvPrediction)
        scrollConversation    = findViewById(R.id.scrollConversation)
        conversationContainer = findViewById(R.id.conversationContainer)
        btnFSLMode            = findViewById(R.id.btnFSLMode)
        btnSpeechMode         = findViewById(R.id.btnSpeechMode)
        tvFSLStatus           = findViewById(R.id.tvFSLStatus)
        tvSpeechStatus        = findViewById(R.id.tvSpeechStatus)
        tvLanguageToggle      = findViewById(R.id.tvLanguageToggle)
        letterBufferPanel     = findViewById(R.id.letterBufferPanel)
        letterChipsContainer  = findViewById(R.id.letterChipsContainer)
        btnConfirmWord        = findViewById(R.id.btnConfirmWord)
        btnDeleteWord         = findViewById(R.id.btnDeleteWord)
        top3Panel             = findViewById(R.id.top3Panel)
        btnChoice1            = findViewById(R.id.btnChoice1)
        btnChoice2            = findViewById(R.id.btnChoice2)
        btnChoice3            = findViewById(R.id.btnChoice3)
        tvChoice1             = findViewById(R.id.tvChoice1)
        tvChoice2             = findViewById(R.id.tvChoice2)
        tvChoice3             = findViewById(R.id.tvChoice3)
        tvConf1               = findViewById(R.id.tvConf1)
        tvConf2               = findViewById(R.id.tvConf2)
        tvConf3               = findViewById(R.id.tvConf3)
        btnDismissTop3        = findViewById(R.id.btnDismissTop3)
    }

    private fun setupTop3Panel() {
        btnChoice1.setOnClickListener { selectChoice(0) }
        btnChoice2.setOnClickListener { selectChoice(1) }
        btnChoice3.setOnClickListener { selectChoice(2) }
        btnDismissTop3.setOnClickListener { top3Panel.visibility = View.GONE }
    }

    private fun setupLetterBuffer() {
        btnConfirmWord.setOnClickListener {
            val word = viewModel.wordAssembly.confirm()
            if (word.isNotBlank()) {
                viewModel.addDeafMessage(word)
                tvPrediction.text = "Show your hands..."
            }
            updateLetterBuffer()
        }
        btnDeleteWord.setOnClickListener {
            viewModel.wordAssembly.clear()
            updateLetterBuffer()
            tvPrediction.text = "Show your hands..."
        }
    }

    private fun setupModeButtons() {
        btnFSLMode.setOnClickListener { switchToFSLMode() }
        btnSpeechMode.setOnClickListener { handleSpeechButtonClick() }
        tvLanguageToggle.setOnClickListener {
            if (viewModel.voskService.isModelLoading) {
                Toast.makeText(this, "Please wait — model loading...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (viewModel.voskService.getCurrentLanguage() == VoskSTTService.Language.FILIPINO) {
                viewModel.voskService.switchLanguage(VoskSTTService.Language.ENGLISH)
                tvLanguageToggle.text = "⏳ Loading English..."
            } else {
                viewModel.voskService.switchLanguage(VoskSTTService.Language.FILIPINO)
                tvLanguageToggle.text = "⏳ Loading Filipino..."
            }
        }
    }

    private fun setupFSLCallbacks() {
        viewModel.fslService.onGestureRecognized = { gesture ->
            runOnUiThread { tvPrediction.text = gesture.uppercase() }
        }
        viewModel.fslService.onLetterDetected = { letter ->
            runOnUiThread { tvPrediction.text = letter }
        }
        viewModel.fslService.onTop3Ready = { top3 ->
            runOnUiThread { showTop3(top3) }
        }
    }

    private fun setupVoskCallbacks() {
        viewModel.voskService.onModelReady = {
            runOnUiThread {
                val lang = viewModel.voskService.getCurrentLanguage()
                tvLanguageToggle.text = if (lang == VoskSTTService.Language.FILIPINO) "🇵🇭 Filipino" else "🇺🇸 English"
                tvSpeechStatus.text = "Speech Input — Tap to speak"
                tvPrediction.text = "Tap mic to speak..."
            }
        }
        viewModel.voskService.onTranscriptionResult = { text ->
            runOnUiThread {
                viewModel.addHearingMessage(text)
                tvPrediction.text = "Tap mic to speak..."
            }
        }
        viewModel.voskService.onRecordingStateChanged = { state ->
            runOnUiThread {
                when (state) {
                    VoskSTTService.RecordingState.RECORDING -> {
                        tvSpeechStatus.text = "Recording... Tap to stop"
                        btnSpeechMode.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                        tvPrediction.text = "Listening..."
                    }
                    VoskSTTService.RecordingState.PROCESSING -> {
                        tvSpeechStatus.text = "Processing..."
                        tvPrediction.text = "Processing speech..."
                    }
                    VoskSTTService.RecordingState.DORMANT -> {
                        tvSpeechStatus.text = "Speech Input — Tap to speak"
                        btnSpeechMode.setBackgroundColor(ContextCompat.getColor(this, R.color.speech_active))
                        tvPrediction.text = "Tap mic to speak..."
                        viewModel.isRecording.value = false
                    }
                }
            }
        }
    }

    private var lastRenderedCount = 0

    private fun observeViewModel() {
        viewModel.entries.observe(this) { entries ->
            val newEntries = entries.drop(lastRenderedCount)
            newEntries.forEach { entry -> renderBubble(entry) }
            lastRenderedCount = entries.size
            if (newEntries.isNotEmpty()) {
                scrollConversation.post { scrollConversation.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
    }

    private fun renderBubble(entry: ConversationEntry) {
        val bubble = ConversationBubbleWidget.create(this, entry)
        conversationContainer.addView(bubble)
    }

    private fun handleSpeechButtonClick() {
        val recording = viewModel.isRecording.value ?: false
        if (viewModel.isFSLMode.value == false) {
            if (!recording) {
                viewModel.isRecording.value = true
                viewModel.voskService.startRecording()
            } else {
                viewModel.voskService.stopRecording()
            }
        } else {
            switchToSpeechMode()
        }
    }

    private fun switchToFSLMode() {
        if (viewModel.isRecording.value == true) viewModel.voskService.stopRecording()
        viewModel.switchToFSL()
        top3Panel.visibility = View.GONE
        updateLetterBuffer()
        btnFSLMode.setBackgroundColor(ContextCompat.getColor(this, R.color.fsl_active))
        btnSpeechMode.setBackgroundColor(ContextCompat.getColor(this, R.color.speech_inactive))
        tvFSLStatus.text = "FSL Camera — Active"
        tvSpeechStatus.text = "Speech Input — Dormant"
        tvPrediction.text = "Show your hands..."
    }

    private fun switchToSpeechMode() {
        viewModel.switchToSpeech()
        top3Panel.visibility = View.GONE
        updateLetterBuffer()
        btnFSLMode.setBackgroundColor(ContextCompat.getColor(this, R.color.fsl_inactive))
        btnSpeechMode.setBackgroundColor(ContextCompat.getColor(this, R.color.speech_active))
        tvFSLStatus.text = "FSL Camera — Dormant"
        tvSpeechStatus.text = "Speech Input — Loading..."
        tvPrediction.text = "Loading speech model..."
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
                .setTargetResolution(android.util.Size(640, 480))
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (viewModel.isFSLMode.value == true) {
                            viewModel.fslService.processFrame(imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    }
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showTop3(top3: List<Pair<String, Float>>) {
        if (top3.isEmpty() || viewModel.isFSLMode.value == false) return
        top3Labels = top3.map { it.first }
        val views = listOf(Pair(tvChoice1, tvConf1), Pair(tvChoice2, tvConf2), Pair(tvChoice3, tvConf3))
        val buttons = listOf(btnChoice1, btnChoice2, btnChoice3)
        top3.forEachIndexed { i, (label, conf) ->
            views[i].first.text = label.uppercase()
            views[i].second.text = "${(conf * 100).toInt()}%"
            buttons[i].setBackgroundColor(
                if (i == 0) Color.parseColor("#1A237E") else Color.parseColor("#263238")
            )
        }
        top3Panel.visibility = View.VISIBLE
    }

    private fun selectChoice(index: Int) {
        if (index >= top3Labels.size) return
        val selected = top3Labels[index]
        if (selected.length == 1 || selected.all { it.isLetter() && it.isUpperCase() }) {
            viewModel.wordAssembly.addLetter(selected)
            updateLetterBuffer()
            tvPrediction.text = selected
        } else {
            viewModel.addDeafMessage(selected)
            tvPrediction.text = selected.uppercase()
        }
        top3Panel.visibility = View.GONE
        viewModel.fslService.clearBuffer()
    }

    private fun updateLetterBuffer() {
        val buffer = viewModel.wordAssembly.getBuffer()
        letterChipsContainer.removeAllViews()
        if (buffer.isEmpty()) {
            letterBufferPanel.visibility = View.GONE
            return
        }
        letterBufferPanel.visibility = View.VISIBLE
        for (letter in buffer) {
            val chip = TextView(this)
            chip.text = letter
            chip.textSize = 16f
            chip.setTextColor(Color.WHITE)
            chip.setBackgroundColor(Color.parseColor("#3949AB"))
            chip.setPadding(20, 10, 20, 10)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.marginEnd = 8
            chip.layoutParams = params
            letterChipsContainer.addView(chip)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && allPermissionsGranted()) startCamera()
        else Toast.makeText(this, "Camera and microphone permissions required", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
