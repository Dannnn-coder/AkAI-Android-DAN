package com.akai

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.akai.data.AppPreferences
import com.akai.data.ConversationEntry
import com.akai.ui.CoachMarkTutorial
import com.akai.ui.ConversationBubbleWidget
import com.akai.ui.HelpModeOverlay
import com.akai.viewmodel.ConversationViewModel
import com.akai.service.FSLRecognitionService
import com.akai.service.VoicePersonaCatalog
import com.akai.service.VoskSTTService
import com.akai.service.looksLikeTagalog
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var viewModel: ConversationViewModel
    private var textToSpeech: TextToSpeech? = null
    private var isTextToSpeechReady = false

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
    private lateinit var btnSettings: TextView
    private lateinit var btnHelp: TextView
    private lateinit var modeSwitcherPanel: LinearLayout

    // Letter buffer
    private lateinit var letterBufferPanel: LinearLayout
    private lateinit var letterChipsContainer: LinearLayout
    private lateinit var btnConfirmWord: TextView
    private lateinit var btnDeleteWord: TextView
    private lateinit var tvSentenceDraft: TextView
    private lateinit var btnFingerspell: TextView
    private lateinit var btnSendSentence: TextView
    private val sentenceWords = mutableListOf<String>()
    private var isFingerspelling = false

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
        textToSpeech = TextToSpeech(this, this)

        bindViews()
        setupTop3Panel()
        setupSentenceBuilder()
        setupLetterBuffer()
        setupSettings()
        setupModeButtons()
        setupFSLCallbacks()
        setupVoskCallbacks()
        observeViewModel()

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS)

        cameraExecutor = Executors.newSingleThreadExecutor()

        maybeShowOnboarding()
    }

    private fun maybeShowOnboarding() {
        val hasSeenOnboarding = getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(AppPreferences.KEY_HAS_SEEN_ONBOARDING, false)
        if (hasSeenOnboarding) return
        findViewById<View>(android.R.id.content).post {
            AlertDialog.Builder(this)
                .setTitle("Welcome to AkAI")
                .setMessage("Is this your first time using AkAI?")
                .setPositiveButton("Yes, show me around") { _, _ -> startCoachMarkTutorial() }
                .setNegativeButton("Skip") { _, _ -> markOnboardingSeen() }
                .setCancelable(false)
                .show()
        }
    }

    private fun startCoachMarkTutorial() {
        val steps = listOf(
            CoachMarkTutorial.Step(
                cameraPreview, "Signing to AkAI",
                "Sign in front of the camera and AkAI will turn it into text the other person can read."
            ),
            CoachMarkTutorial.Step(
                btnFingerspell, "Fingerspelling",
                "Can't find a sign for a word? Spell it out letter by letter instead, then tap each letter to add it."
            ),
            CoachMarkTutorial.Step(
                btnSpeechMode, "Speaking to AkAI",
                "Tap the microphone and just talk — in Filipino or English. AkAI will type out what you say."
            ),
            CoachMarkTutorial.Step(
                modeSwitcherPanel, "Switching Modes",
                "Tap here anytime to switch between signing with the camera and speaking with the microphone."
            ),
            CoachMarkTutorial.Step(
                scrollConversation, "Shared Conversation",
                "Everything you sign or say shows up here, so both of you can follow along together."
            ),
            CoachMarkTutorial.Step(
                btnHelp, "Need Help Later?",
                "Stuck later on? Tap the ? icon anytime and I'll walk you through everything again."
            )
        )
        CoachMarkTutorial(this, steps) { markOnboardingSeen() }.start()
    }

    private fun markOnboardingSeen() {
        getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(AppPreferences.KEY_HAS_SEEN_ONBOARDING, true)
            .apply()
    }

    private fun startHelpMode() {
        val components = listOf(
            CoachMarkTutorial.Step(
                cameraPreview, "The Camera",
                "This is where AkAI watches you sign. Make sure your hands are clearly in view."
            ),
            CoachMarkTutorial.Step(
                tvPrediction, "What AkAI Sees",
                "Shows the word or letter AkAI thinks you just signed."
            ),
            CoachMarkTutorial.Step(
                btnFingerspell, "Fingerspell",
                "Can't find the sign for a word? Tap this and spell it out letter by letter instead."
            ),
            CoachMarkTutorial.Step(
                tvSentenceDraft, "Your Sentence",
                "Your signs get added here as words. Once it looks right, hit Send."
            ),
            CoachMarkTutorial.Step(
                btnSendSentence, "Send",
                "Adds your sentence to the conversation so the other person can read it."
            ),
            CoachMarkTutorial.Step(
                btnFSLMode, "FSL Camera",
                "Tap this to turn on the camera and start signing."
            ),
            CoachMarkTutorial.Step(
                btnSpeechMode, "Speech Input",
                "Tap this to turn on the microphone. Speak normally and AkAI will type out what you said."
            ),
            CoachMarkTutorial.Step(
                tvLanguageToggle, "Language",
                "Tap to switch between Filipino and English for speech."
            ),
            CoachMarkTutorial.Step(
                scrollConversation, "Conversation",
                "Every message you send or receive shows up here, so you can follow the whole conversation."
            ),
            CoachMarkTutorial.Step(
                btnSettings, "Settings",
                "Change the color of your chat bubbles or pick a voice for AkAI to read messages out loud."
            )
        )
        HelpModeOverlay(this, components) {}.start()
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
        btnSettings           = findViewById(R.id.btnSettings)
        btnHelp               = findViewById(R.id.btnHelp)
        modeSwitcherPanel     = findViewById(R.id.modeSwitcherPanel)
        letterBufferPanel     = findViewById(R.id.letterBufferPanel)
        letterChipsContainer  = findViewById(R.id.letterChipsContainer)
        btnConfirmWord        = findViewById(R.id.btnConfirmWord)
        btnDeleteWord         = findViewById(R.id.btnDeleteWord)
        tvSentenceDraft       = findViewById(R.id.tvSentenceDraft)
        btnFingerspell        = findViewById(R.id.btnFingerspell)
        btnSendSentence       = findViewById(R.id.btnSendSentence)
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

    private fun setupSettings() {
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        btnHelp.setOnClickListener { startHelpMode() }
    }

    private fun settingsPrefs(): SharedPreferences {
        return getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
    }

    private fun getDeafBubbleColor(): Int {
        return settingsPrefs().getInt(AppPreferences.KEY_DEAF_BUBBLE_COLOR, AppPreferences.DEFAULT_DEAF_BUBBLE_COLOR)
    }

    private fun getHearingBubbleColor(): Int {
        return settingsPrefs().getInt(AppPreferences.KEY_HEARING_BUBBLE_COLOR, AppPreferences.DEFAULT_HEARING_BUBBLE_COLOR)
    }

    private fun setupSentenceBuilder() {
        btnFingerspell.setOnClickListener { enterFingerspellMode() }
        btnSendSentence.setOnClickListener { sendSentenceDraft() }
        updateSentenceDraft()
        exitFingerspellMode()
    }

    private fun setupLetterBuffer() {
        btnConfirmWord.setOnClickListener {
            val word = viewModel.wordAssembly.confirm()
            if (word.isNotBlank()) {
                addWordToSentence(word)
                exitFingerspellMode()
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

    private fun addWordToSentence(word: String) {
        val normalized = word.trim()
        if (normalized.isBlank()) return
        sentenceWords.add(normalized)
        updateSentenceDraft()
    }

    private fun updateSentenceDraft() {
        val sentence = sentenceWords.joinToString(" ")
        if (sentence.isBlank()) {
            tvSentenceDraft.text = getString(R.string.sentence_builder_empty)
            tvSentenceDraft.setTextColor(Color.parseColor("#BDBDBD"))
        } else {
            tvSentenceDraft.text = sentence
            tvSentenceDraft.setTextColor(Color.WHITE)
        }
    }

    private fun sendSentenceDraft() {
        val sentence = sentenceWords.joinToString(" ").trim()
        if (sentence.isBlank()) {
            Toast.makeText(this, "Add words before sending", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.addDeafMessage(sentence)
        sentenceWords.clear()
        updateSentenceDraft()
        tvPrediction.text = "Show your hands..."
    }

    private fun enterFingerspellMode() {
        isFingerspelling = true
        viewModel.wordAssembly.clear()
        updateLetterBuffer()
        viewModel.fslService.recognitionMode = FSLRecognitionService.RecognitionMode.LETTERS
        viewModel.fslService.clearBuffer()
        top3Panel.visibility = View.GONE
        btnFingerspell.setBackgroundColor(Color.parseColor("#1A237E"))
        btnFingerspell.text = "Spelling..."
        tvPrediction.text = "Fingerspell a word..."
    }

    private fun exitFingerspellMode() {
        isFingerspelling = false
        viewModel.fslService.recognitionMode = FSLRecognitionService.RecognitionMode.WORDS
        viewModel.fslService.clearBuffer()
        top3Panel.visibility = View.GONE
        btnFingerspell.setBackgroundColor(Color.parseColor("#3949AB"))
        btnFingerspell.text = "Fingerspell"
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
        val bubble = ConversationBubbleWidget.create(
            context = this,
            entry = entry,
            onDeafMessageSpeak = ::speakDeafMessage,
            deafBubbleColor = getDeafBubbleColor(),
            hearingBubbleColor = getHearingBubbleColor()
        )
        conversationContainer.addView(bubble)
    }

    override fun onResume() {
        super.onResume()
        if (::conversationContainer.isInitialized && lastRenderedCount > 0) {
            refreshConversation()
        }
    }

    private fun refreshConversation() {
        val entries = viewModel.entries.value ?: emptyList()
        conversationContainer.removeAllViews()
        entries.forEach { entry -> renderBubble(entry) }
        lastRenderedCount = entries.size
        if (entries.isNotEmpty()) {
            scrollConversation.post { scrollConversation.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onInit(status: Int) {
        isTextToSpeechReady = status == TextToSpeech.SUCCESS &&
            (textToSpeech?.isLanguageAvailable(Locale.US) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
        if (!isTextToSpeechReady) {
            Toast.makeText(this, "Text-to-speech unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    /** Picks the right locale + voice for [text] from the user's chosen persona, so the same
     *  "person" reads English text in English and Tagalog text in Tagalog. */
    private fun configureTextToSpeechFor(text: String): Boolean {
        val tts = textToSpeech ?: return false
        val tagalog = looksLikeTagalog(text)
        val preferredLocale = if (tagalog) Locale("fil", "PH") else Locale.US
        val locale = if (tts.isLanguageAvailable(preferredLocale) >= TextToSpeech.LANG_AVAILABLE) {
            preferredLocale
        } else {
            Locale.US
        }
        if (tts.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) return false

        tts.language = locale
        tts.setPitch(1.0f)
        tts.setSpeechRate(1.0f)

        val personaName = settingsPrefs().getString(AppPreferences.KEY_TTS_VOICE_PERSONA, null) ?: "Alex"
        val persona = VoicePersonaCatalog.build(tts).firstOrNull { it.name == personaName }
        val voice = if (tagalog) persona?.filipinoVoice ?: persona?.englishVoice else persona?.englishVoice
        if (voice != null) tts.voice = voice

        return true
    }

    private fun speakDeafMessage(text: String) {
        isTextToSpeechReady = configureTextToSpeechFor(text)
        if (!isTextToSpeechReady) {
            Toast.makeText(this, "Install an offline text-to-speech voice to read messages aloud", Toast.LENGTH_SHORT).show()
            return
        }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "deaf-message-${System.currentTimeMillis()}")
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
        exitFingerspellMode()
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
        exitFingerspellMode()
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
        buttons.forEachIndexed { i, button ->
            button.visibility = if (i < top3.size) View.VISIBLE else View.GONE
        }
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
        if (isFingerspelling) {
            viewModel.wordAssembly.addLetter(selected)
            updateLetterBuffer()
            tvPrediction.text = selected
        } else {
            addWordToSentence(selected)
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
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        cameraExecutor.shutdown()
    }
}
