package com.akai

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.akai.data.AppPreferences
import com.akai.data.ConversationEntry
import com.akai.data.SenderType
import com.akai.ui.BubbleSide
import com.akai.ui.AkaiDialog
import com.akai.ui.AkaiNotification
import com.akai.ui.CoachMarkTutorial
import com.akai.ui.ContextualHelpOverlay
import com.akai.ui.ConversationBubbleWidget
import com.akai.ui.LanguageLoadingBar
import com.akai.ui.ModeTransitionOverlay
import com.akai.ui.VoiceWaveformView
import com.akai.viewmodel.ConversationViewModel
import com.akai.viewmodel.ConnectionState
import com.akai.service.FSLRecognitionService
import com.akai.service.VoicePersonaCatalog
import com.akai.service.VoskSTTService
import com.akai.service.looksLikeTagalog
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** The two possible destinations of the full-screen mode transition. */
private enum class ModeTarget { CAMERA, VOICE }

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
    private lateinit var imgFSLIcon: android.widget.ImageView
    private lateinit var imgSpeechIcon: android.widget.ImageView
    private lateinit var btnSettings: android.widget.ImageButton
    private lateinit var btnHelp: android.widget.ImageButton
    private lateinit var btnDictionary: android.widget.ImageButton
    private lateinit var btnDualDevice: android.widget.ImageButton
    private lateinit var btnResetConversation: android.widget.ImageButton
    private lateinit var modeSwitcherPanel: LinearLayout

    // Dual Device flow overlays (the session itself is UI-agnostic)
    private var dualDeviceOverlay: com.akai.ui.DualDeviceOverlay? = null
    private var connectedSessionOverlay: com.akai.ui.ConnectedSessionOverlay? = null
    private var hostSessionOverlay: com.akai.ui.HostSessionOverlay? = null
    private var joinSessionOverlay: com.akai.ui.JoinSessionOverlay? = null
    private var pendingJoinCode: String? = null

    // Letter buffer
    private lateinit var letterBufferPanel: LinearLayout
    private lateinit var tvLetterWord: TextView
    private lateinit var btnConfirmWord: android.widget.ImageButton
    private lateinit var btnDeleteWord: android.widget.ImageButton
    private lateinit var tvSentenceDraft: TextView
    private lateinit var scrollSentenceDraft: HorizontalScrollView
    private lateinit var btnBackspace: android.widget.ImageButton
    private lateinit var btnFingerspell: android.widget.ImageButton
    private lateinit var btnSendSentence: android.widget.ImageButton
    private val sentenceWords = mutableListOf<String>()
    private var isFingerspelling = false

    // Voice mode overlay (blue camera-replacement area)
    private lateinit var voiceOverlay: View
    private lateinit var btnTapToSpeak: TextView
    private lateinit var voiceWaveform: VoiceWaveformView

    // FSL recognition / result area
    private lateinit var fslRecognitionPanel: View

    // Voice-mode language controls (inside the fixed result container)
    private lateinit var voiceControlsPanel: LinearLayout
    private lateinit var tvLanguageLabel: TextView
    private lateinit var btnLangEnglish: TextView
    private lateinit var btnLangFilipino: TextView
    private lateinit var languageButtonsRow: LinearLayout
    private lateinit var languageLoadingState: LinearLayout
    private lateinit var tvLanguageLoadingText: TextView
    private lateinit var languageLoadingBar: LanguageLoadingBar

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
    private lateinit var btnDismissTop3: android.widget.ImageButton
    private var top3Labels = listOf<String>()
    private lateinit var sessionTag: android.widget.LinearLayout
    private lateinit var sessionTagCode: android.widget.TextView

    private lateinit var cameraExecutor: ExecutorService
    private var cameraRunning = false

    // Full-screen Camera <-> Voice transition
    private val mainHandler = Handler(Looper.getMainLooper())
    private var modeSwitchInProgress = false
    private var transitionStartMs = 0L

    companion object {
        private const val REQUEST_PERMISSIONS = 100
        private const val REQUEST_NEARBY_PERMISSIONS = 101
        // Give the overlay a guaranteed minimum visible presence so a very fast
        // switch never reads as an accidental flash. The wait itself is always
        // tied to real readiness, never an artificial fixed load.
        private const val MODE_SWITCH_MIN_VISIBLE_MS = 500L
        // Pause AFTER the panel fully covers the screen, before the real mode
        // switch runs — user never sees the underlying UI change mid-animation.
        private const val MODE_SWITCH_COVER_PAUSE_MS = 250L
        // Absolute cap before the in-container language-loading state gives up
        // and restores the buttons (Filipino model copy can be slow on first run).
        private const val LANGUAGE_LOAD_TIMEOUT_MS = 12000L
        // Absolute cap before the transition gives up and restores the previous mode.
        private const val MODE_READY_TIMEOUT_MS = 8000L
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeFromPreference()
        setContentView(R.layout.activity_main)

        // Sync the system status/navigation bars with the active theme.
        SystemBarTheme.apply(this)

        // Edge-to-edge fix: on Android 15+ (targetSdk 35+) apps draw behind the system
        // status/navigation bars by default, which made the phone's nav buttons overlap the
        // camera / Fingerspell / Help buttons. Pad the root view by the system bar insets so
        // no UI hides behind the bars.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

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
        observeSessionState()

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
            // First-time instructions use a fully MODAL custom AkAI dialog — only
            // the two buttons below can be pressed, everything else is blocked.
            AkaiDialog.show(
                activity = this,
                title = "Welcome to AkAI",
                message = "Is this your first time using AkAI?",
                buttons = listOf(
                    AkaiDialog.Button("Skip", textOnly = true) { markOnboardingSeen() },
                    AkaiDialog.Button("Yes, show me around") { startCoachMarkTutorial() }
                )
            )
        }
    }

    private fun startCoachMarkTutorial() {
        val steps = listOf(
            CoachMarkTutorial.Step(
                btnResetConversation, "Reset Conversation",
                "This button clears the whole conversation from the screen. Tap it and confirm if the chat gets too messy to follow."
            ),
            CoachMarkTutorial.Step(
                cameraPreview, "Signing to AkAI",
                "Sign in front of the camera. AkAI watches your hand movements and turns your Filipino Sign Language into text the other person can read."
            ),
            CoachMarkTutorial.Step(
                btnFingerspell, "Fingerspelling",
                "Can't find a sign for a word? Tap this to spell it out letter by letter instead."
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

    /**
     * Helps reflects the CURRENT mode by pointing directly at the REAL controls on
     * screen (point-and-click, no card list/accordion):
     *  - The dimmed-but-visible screen IS the interface: tap a component and its
     *    description appears in the stable caption while its real bounds light up.
     *  - PRESERVES every existing component (Camera, What AkAI Sees / Language,
     *    Fingerspell, Your Sentence, Send, FSL Camera, Speech Input, Conversation,
     *    Settings) plus the newer Dictionary, Dual Device, Reset Conversation,
     *    Backspace, Tap to Speak and Help.
     *  - In Voice Mode "What AkAI Sees" is REPLACED by "Language".
     *  - "Audio" is included (in BOTH modes) only when a Deaf/FSL message that has
     *    a visible speaker button is in the conversation right now.
     */
    private fun startHelpMode() {
        val cameraMode = viewModel.isFSLMode.value == true
        val audioButton = latestVisibleDeafAudioButton()
        val targets = mutableListOf<ContextualHelpOverlay.HelpTarget>()

        if (cameraMode) {
            // --- Existing Camera Mode components (do NOT remove) ---
            targets.add(
                ContextualHelpOverlay.HelpTarget(
                    cameraPreview, "Camera",
                    "This is where AkAI watches you sign. Keep your hands clearly in view and move slowly."
                )
            )
            targets.add(
                ContextualHelpOverlay.HelpTarget(
                    tvPrediction, "What AkAI Sees",
                    "The space where AkAI shows the sign it recognized - the word or letter, plus its confidence."
                )
            )
            targets.add(
                ContextualHelpOverlay.HelpTarget(
                    btnFingerspell, "Fingerspell",
                    "Can't find a sign for a word? Tap Fingerspell to spell it out one letter at a time."
                )
            )
        } else {
            // --- Existing Voice Mode components (do NOT remove) ---
            targets.add(
                ContextualHelpOverlay.HelpTarget(
                    languageButtonsRow, "Language",
                    "Choose whether AkAI's speech recognition listens for English or Filipino."
                )
            )
            targets.add(
                ContextualHelpOverlay.HelpTarget(
                    btnTapToSpeak, "Tap to Speak",
                    "Tap to start speaking, then tap again to stop. AkAI turns your words into text."
                )
            )
        }

        // --- Shared existing components (present in BOTH modes) ---
        targets.add(
            ContextualHelpOverlay.HelpTarget(
                scrollSentenceDraft, "Your Sentence",
                "Your recognized signs (and voice words) build up here as a sentence before you send it."
            )
        )
        targets.add(
            ContextualHelpOverlay.HelpTarget(
                btnSendSentence, "Send",
                "Adds your sentence to the conversation so the other person can read it."
            )
        )
        targets.add(
            ContextualHelpOverlay.HelpTarget(
                btnFSLMode, "FSL Camera",
                "Switches AkAI to Camera Mode so you can use sign recognition."
            )
        )
        targets.add(
            ContextualHelpOverlay.HelpTarget(
                btnSpeechMode, "Speech Input",
                "Switches AkAI to Voice Mode so you can speak instead of sign."
            )
        )
        targets.add(
            ContextualHelpOverlay.HelpTarget(
                scrollConversation, "Conversation",
                "The chat thread where every message - signed or spoken - appears for both of you."
            )
        )
        targets.add(
            ContextualHelpOverlay.HelpTarget(
                btnSettings, "Settings",
                "Customize chat bubble colors, text-to-speech voice, theme, and more."
            )
        )

        // --- Newer additions, just like the requested list ---
        targets.add(
            ContextualHelpOverlay.HelpTarget(
                btnDictionary, "Dictionary",
                "Browse the Filipino Sign Language signs and vocabulary available offline in AkAI."
            )
        )
        targets.add(
            ContextualHelpOverlay.HelpTarget(
                btnDualDevice, "Dual Device",
                "Connect another phone so both of you can communicate through the same conversation - no internet needed."
            )
        )
        targets.add(
            ContextualHelpOverlay.HelpTarget(
                btnResetConversation, "Reset Conversation",
                "Clears the whole conversation from both devices."
            )
        )
        targets.add(
            ContextualHelpOverlay.HelpTarget(
                btnBackspace, "Backspace",
                "Removes the most recent letter, sign, or word from the sentence you're building."
            )
        )
        targets.add(
            ContextualHelpOverlay.HelpTarget(
                btnHelp, "Help",
                "Opens this same point-and-click guide whenever you need it."
            )
        )
        if (audioButton != null) {
            targets.add(
                ContextualHelpOverlay.HelpTarget(
                    audioButton, "Audio",
                    "Plays the sound attached to a Deaf/FSL message - the speaker button on that bubble."
                )
            )
        }
        ContextualHelpOverlay(this, targets, tutorial = false) {}.start()
    }

    /** The speaker button of the most recent Deaf/FSL bubble that currently shows
     *  one on screen (the Audio help target), or null when none is visible. */
    private fun latestVisibleDeafAudioButton(): View? {
        for (i in conversationContainer.childCount - 1 downTo 0) {
            val child = conversationContainer.getChildAt(i)
            if (child is ConversationBubbleWidget) {
                child.audioButtonOrNull()?.let { return it }
            }
        }
        return null
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
        imgFSLIcon            = findViewById(R.id.imgFSLIcon)
        imgSpeechIcon         = findViewById(R.id.imgSpeechIcon)
        btnSettings           = findViewById(R.id.btnSettings)
        btnHelp               = findViewById(R.id.btnHelp)
        btnDictionary         = findViewById(R.id.btnDictionary)
        btnDualDevice         = findViewById(R.id.btnDualDevice)
        btnResetConversation  = findViewById(R.id.btnResetConversation)
        modeSwitcherPanel     = findViewById(R.id.modeSwitcherPanel)
        letterBufferPanel     = findViewById(R.id.letterBufferPanel)
        tvLetterWord          = findViewById(R.id.tvLetterWord)
        btnConfirmWord        = findViewById(R.id.btnConfirmWord)
        btnDeleteWord         = findViewById(R.id.btnDeleteWord)
        tvSentenceDraft       = findViewById(R.id.tvSentenceDraft)
        scrollSentenceDraft   = findViewById(R.id.scrollSentenceDraft)
        btnBackspace          = findViewById(R.id.btnBackspace)
        btnFingerspell        = findViewById(R.id.btnFingerspell)
        btnSendSentence       = findViewById(R.id.btnSendSentence)
        voiceOverlay          = findViewById(R.id.voiceOverlay)
        btnTapToSpeak         = findViewById(R.id.btnTapToSpeak)
        voiceWaveform         = findViewById(R.id.voiceWaveform)
        fslRecognitionPanel   = findViewById(R.id.fslRecognitionPanel)
        voiceControlsPanel    = findViewById(R.id.voiceControlsPanel)
        tvLanguageLabel       = findViewById(R.id.tvLanguageLabel)
        btnLangEnglish        = findViewById(R.id.btnLangEnglish)
        btnLangFilipino       = findViewById(R.id.btnLangFilipino)
        languageButtonsRow    = findViewById(R.id.languageButtonsRow)
        languageLoadingState  = findViewById(R.id.languageLoadingState)
        tvLanguageLoadingText = findViewById(R.id.tvLanguageLoadingText)
        languageLoadingBar    = findViewById(R.id.languageLoadingBar)
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
        sessionTag            = findViewById(R.id.sessionTag)
        sessionTagCode        = findViewById(R.id.sessionTagCode)
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
        btnDictionary.setOnClickListener {
            startActivity(Intent(this, DictionaryActivity::class.java))
        }
        // Dual Device opens the full-screen Host/Join screen (slides up; down on the
        // X exit and on a successful join). Networking stays on the unchanged flow.
        btnDualDevice.setOnClickListener {
            // While a session is ACTIVE, the Dual Device button reopens the
            // "Connected to Session" page; the Host/Join selector is only reachable
            // once the session has ended.
            if (viewModel.connectionState.value == ConnectionState.CONNECTED) {
                if (connectedSessionOverlay == null) openConnectedSessionScreen()
            } else if (dualDeviceOverlay == null) {
                openDualDeviceScreen()
            }
        }
        btnResetConversation.setOnClickListener { confirmResetConversation() }
    }

    /** Full-screen Dual Device screen: Host/Join pages plus the X exit.
     *  [animate] controls whether it slides up (opening from the main screen) or
     *  pops in place (returning via Cancel from a session page). */
    private fun openDualDeviceScreen(animate: Boolean = true) {
        val overlay = com.akai.ui.DualDeviceOverlay(this)
        dualDeviceOverlay = overlay
        overlay.onExit = { closeDualDeviceScreen() }
        overlay.btnHost.setOnClickListener { openHostSessionScreen() }
        overlay.btnJoin.setOnClickListener { openJoinSessionScreen() }
        overlay.setInSession(viewModel.connectionState.value != ConnectionState.IDLE)
        (window.decorView as ViewGroup).addView(overlay)
        if (animate) overlay.enter() else overlay.showNow()
    }

    /** Slides the Dual Device screen down and away (or runs [afterExit] once it is
     *  fully off-screen — used for the post-exit session actions & dialogs). */
    private fun closeDualDeviceScreen(afterExit: () -> Unit = {}) {
        val overlay = dualDeviceOverlay ?: return
        overlay.exit {
            dualDeviceOverlay = null
            afterExit()
        }
    }

    /** Removes the Dual Device screen instantly (no animation) when a session page
     *  navigates on top of it. */
    private fun closeDualDeviceScreenInstantly() {
        val overlay = dualDeviceOverlay ?: return
        dualDeviceOverlay = null
        overlay.dismissNow()
    }

    // =====================================================================
    //  DUAL DEVICE FLOW — "Connected to Session" page
    // =====================================================================

    /** Opens the full-screen "Connected to Session" page (same instant-pop idiom as
     *  the Host/Join pages). The X ([ConnectedSessionOverlay.onExit]) merely closes
     *  this page back to the live conversation — the session STAYS connected; only
     *  the End Session / Disconnect action really tears it down. */
    private fun openConnectedSessionScreen() {
        if (connectedSessionOverlay != null) return
        val overlay = com.akai.ui.ConnectedSessionOverlay(this)
        connectedSessionOverlay = overlay
        overlay.setCode(viewModel.sessionCode.value ?: "")
        overlay.setActionLabel(
            if (viewModel.isHost.value == true) getString(R.string.dual_connected_end)
            else getString(R.string.dual_connected_disconnect)
        )
        overlay.onExit = { closeConnectedSessionPage() }
        overlay.onAction = { endDualDeviceSession() }
        (window.decorView as ViewGroup).addView(overlay)
        overlay.showNow()
    }

    /** X on the "Connected to Session" page: slide it back down to reveal the
     *  conversation. The session and the shared thread remain active. */
    private fun closeConnectedSessionPage() {
        val overlay = connectedSessionOverlay ?: return
        connectedSessionOverlay = null
        overlay.exit { /* removed by the overlay itself */ }
    }

    /** End Session (Host) / Disconnect (Joiner) from the "Connected to Session" page:
     *  tears down the connection, clears the conversation (RA 10173), and returns
     *  to the Host/Join selector page. */
    private fun endDualDeviceSession() {
        viewModel.endSession()
        AkaiNotification.short(this, "Session ended — conversation cleared")
        connectedSessionOverlay?.dismissNow()
        connectedSessionOverlay = null
        // Wipe rendered bubbles too, since the thread was cleared for RA 10173.
        conversationContainer.removeAllViews()
        lastRenderedCount = 0
        openDualDeviceScreen(animate = false)
    }

    // =====================================================================
    //  DUAL DEVICE FLOW — Host / Join pages (networking lives in the ViewModel)
    // =====================================================================

    /** Dual Device → Host: the Host Session page pops in IMMEDIATELY (no slide), then
     *  the REAL session starts (existing startSession()/Nearby flow). */
    private fun openHostSessionScreen() {
        if (hostSessionOverlay != null) return
        closeDualDeviceScreenInstantly()
        val overlay = com.akai.ui.HostSessionOverlay(this)
        hostSessionOverlay = overlay
        overlay.onCancel = { cancelHostSession() }
        (window.decorView as ViewGroup).addView(overlay)
        overlay.showNow()
        startHosting()
    }

    private fun cancelHostSession() {
        val overlay = hostSessionOverlay ?: return
        hostSessionOverlay = null
        overlay.dismissNow()
        viewModel.endSession()
        openDualDeviceScreen(animate = false)
    }

    /** Permissions gate for hosting; after grant, [doStartSession] runs the existing flow. */
    private fun startHosting() {
        if (hasNearbyPermissions()) doStartSession()
        else requestNearbyPermissions(PendingSyncAction.HOST)
    }

    private fun doStartSession() {
        val code = viewModel.startSession()
        hostSessionOverlay?.let { overlay ->
            overlay.setCode(code)
            overlay.setWaitingText(getString(R.string.dual_host_waiting))
            overlay.showLoading()
        }
    }

    /** Dual Device → Join: the Join Session page pops in IMMEDIATELY (no slide). */
    private fun openJoinSessionScreen() {
        if (joinSessionOverlay != null) return
        closeDualDeviceScreenInstantly()
        val overlay = com.akai.ui.JoinSessionOverlay(this)
        joinSessionOverlay = overlay
        overlay.onCancel = { cancelJoinSession() }
        overlay.onJoin = { submitJoinFromPage() }
        (window.decorView as ViewGroup).addView(overlay)
        overlay.showNow()
    }

    private fun cancelJoinSession() {
        val overlay = joinSessionOverlay ?: return
        joinSessionOverlay = null
        overlay.dismissNow()
        viewModel.endSession()
        openDualDeviceScreen(animate = false)
    }
    /** Reads AK-XXXX from the page and runs the EXISTING joinSession() flow.
     *  Disables the Join button to prevent duplicate attempts while searching. */
    private fun submitJoinFromPage() {
        val overlay = joinSessionOverlay ?: return
        val code = overlay.collectCode()
        if (code == null) {
            AkaiNotification.short(this, "Enter the full 4-character code")
            return
        }
        if (!hasNearbyPermissions()) {
            pendingJoinCode = code
            requestNearbyPermissions(PendingSyncAction.JOIN)
            return
        }
        runJoin(code)
    }

    private fun runJoin(code: String) {
        val overlay = joinSessionOverlay ?: return
        overlay.joinButton.isEnabled = false
        overlay.setStatusText("Searching for session $code…")
        overlay.showLoading()
        viewModel.joinSession(code)
    }

    /** Confirmation dialog before wiping the whole conversation (UI only). */
    private fun confirmResetConversation() {
        AkaiDialog.show(
            activity = this,
            title = "Reset the whole conversation?",
            message = "This will clear the whole conversation. This cannot be undone.",
            buttons = listOf(
                AkaiDialog.Button("Cancel") {},
                AkaiDialog.Button("Yes") {
                    viewModel.clearConversation()
                    conversationContainer.removeAllViews()
                    lastRenderedCount = 0
                    sentenceWords.clear()
                    updateSentenceDraft()
                }
            )
        )
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
        btnBackspace.setOnClickListener { backspaceSentence() }
        // Fingerspell button is a TOGGLE: first tap enters fingerspelling, second exits.
        btnFingerspell.setOnClickListener {
            if (isFingerspelling) exitFingerspellMode() else enterFingerspellMode()
        }
        btnSendSentence.setOnClickListener { sendSentenceDraft() }
        updateSentenceDraft()
        exitFingerspellMode()
    }

    /** UI-only: remove the last word from the sentence draft (Backspace button). */
    private fun backspaceSentence() {
        if (sentenceWords.isNotEmpty()) sentenceWords.removeAt(sentenceWords.lastIndex)
        updateSentenceDraft()
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
            tvSentenceDraft.setTextColor(getThemeColor(R.color.theme_placeholder))
            scrollSentenceDraft.post { scrollSentenceDraft.scrollTo(0, 0) }
        } else {
            tvSentenceDraft.text = sentence
            tvSentenceDraft.setTextColor(getThemeColor(R.color.theme_sentence_text))
            // Auto-scroll ONLY the text to the newest addition; the fixed
            // bg_sentence_input container never moves.
            scrollSentenceDraft.post {
                scrollSentenceDraft.fullScroll(View.FOCUS_RIGHT)
            }
        }
    }

    private fun sendSentenceDraft() {
        val sentence = sentenceWords.joinToString(" ").trim()
        if (sentence.isBlank()) {
            AkaiNotification.short(this, "Add words before sending")
            return
        }
        // Side is decided by the ACTIVE MODE, not the layout plumbing:
        //   Voice mode  -> Hearing/voice message -> RIGHT, no audio button
        //   FSL/camera  -> Deaf/sign   message    -> LEFT,  audio button
        if (viewModel.isFSLMode.value == false) {
            viewModel.addHearingMessage(sentence)
        } else {
            viewModel.addDeafMessage(sentence)
        }
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
        btnFingerspell.setBackgroundResource(R.drawable.bg_circle_green_3d)
        tvPrediction.text = "Fingerspell a word..."
    }

    private fun exitFingerspellMode() {
        isFingerspelling = false
        viewModel.fslService.recognitionMode = FSLRecognitionService.RecognitionMode.WORDS
        viewModel.fslService.clearBuffer()
        top3Panel.visibility = View.GONE
        letterBufferPanel.visibility = View.GONE
        btnFingerspell.setBackgroundResource(R.drawable.bg_circle_blue_3d)
        tvPrediction.text = "Show your hands..."
    }

    private fun setupModeButtons() {
        // Initial mode styling must match the Camera-selected post-onChange state.
        // The XML defaults must NOT be relied on here: the inactive Voice segment
        // would otherwise show theme_text_primary (white in Dark Mode) instead of
        // the brand blue, until the user toggled modes at least once.
        btnFSLMode.setBackgroundResource(R.drawable.bg_mode_selected_3d)
        btnSpeechMode.setBackgroundResource(0)
        tvFSLStatus.setTextColor(Color.WHITE)
        tvSpeechStatus.setTextColor(akaiBlue())
        applyModeIconTint(cameraActive = true, speechActive = false)
        updateLanguageButtons()
        btnFSLMode.setOnClickListener { requestModeSwitch(ModeTarget.CAMERA) }
        // The mode segment itself only ENTERS voice mode; recording is controlled by
        // the dedicated "Tap to speak" button inside the blue voice area.
        btnSpeechMode.setOnClickListener { requestModeSwitch(ModeTarget.VOICE) }
        btnLangEnglish.setOnClickListener { setSpeechLanguage(VoskSTTService.Language.ENGLISH) }
        btnLangFilipino.setOnClickListener { setSpeechLanguage(VoskSTTService.Language.FILIPINO) }
        btnTapToSpeak.setOnClickListener {
            val recording = viewModel.isRecording.value ?: false
            if (recording) {
                viewModel.voskService.stopRecording()
            } else {
                if (viewModel.voskService.isModelLoading) {
                    AkaiNotification.short(this, "Please wait — model loading...")
                } else {
                    viewModel.voskService.startRecording()
                }
            }
        }
    }

    /** Voice-mode language picker: hides the buttons, shows the localized loading
     *  state, then loads the model and only restores the buttons once it's ready. */
    private fun setSpeechLanguage(target: VoskSTTService.Language) {
        if (viewModel.voskService.isModelLoading) {
            AkaiNotification.short(this, "Please wait — model loading...")
            return
        }
        if (viewModel.voskService.getCurrentLanguage() == target) return  // no-op

        setLanguageLoading(loading = true, target = target)
        val accepted = viewModel.voskService.switchLanguage(target)
        if (!accepted) {
            setLanguageLoading(loading = false)
            AkaiNotification.short(this, "Please wait — try again in a moment")
            return
        }
        pollLanguageReady(target) { loadedOk ->
            setLanguageLoading(loading = false)
            if (!loadedOk) {
                AkaiNotification.short(this, "Couldn't load the language model")
            }
        }
    }

    /** Swaps the Language label + buttons for the centered loading state (and back).
     *  The buttons are hidden BEFORE any state changes so the user never sees the
     *  selection flip prematurely — it appears selected only after loading ends. */
    private fun setLanguageLoading(loading: Boolean, target: VoskSTTService.Language? = null) {
        if (loading) {
            tvLanguageLoadingText.text = when (target) {
                VoskSTTService.Language.ENGLISH -> "Loading English Language Model"
                VoskSTTService.Language.FILIPINO -> "Loading Filipino Language Model"
                null -> return
            }
            tvLanguageLabel.visibility = View.GONE
            languageButtonsRow.visibility = View.GONE
            languageLoadingState.visibility = View.VISIBLE
            btnLangEnglish.isEnabled = false
            btnLangFilipino.isEnabled = false
            languageLoadingBar.start()
        } else {
            tvLanguageLabel.visibility = View.VISIBLE
            languageButtonsRow.visibility = View.VISIBLE
            languageLoadingState.visibility = View.GONE
            btnLangEnglish.isEnabled = true
            btnLangFilipino.isEnabled = true
            languageLoadingBar.stop()
            updateLanguageButtons()
        }
    }

    /** Polls until the requested model is genuinely loaded; surfaces failures and
     *  an absolute timeout so the loading state can never stick forever. */
    private fun pollLanguageReady(target: VoskSTTService.Language, onDone: (Boolean) -> Unit) {
        val started = System.currentTimeMillis()
        val poll = object : Runnable {
            override fun run() {
                val vosk = viewModel.voskService
                if (vosk.isLanguageLoaded(target)) {
                    onDone(true)
                    return
                }
                if (!vosk.isModelLoading) {
                    // Load finished WITHOUT the target model ready -> it failed.
                    onDone(false)
                    return
                }
                if (System.currentTimeMillis() - started >= LANGUAGE_LOAD_TIMEOUT_MS) {
                    onDone(false)
                    return
                }
                mainHandler.postDelayed(this, 100L)
            }
        }
        mainHandler.postDelayed(poll, 100L)
    }

    /** Language picker states:
     *   SELECTED  -> PRESSED/ACTIVE appearance: flat #4A6FCC pill, white text.
     *   UNSELECTED -> WHITE appearance: white 3D pill, AkAI blue text.
     *  The visual state always mirrors the actual speech-recognition language. */
    private fun updateLanguageButtons() {
        val englishSelected =
            viewModel.voskService.getCurrentLanguage() == VoskSTTService.Language.ENGLISH
        btnLangEnglish.setBackgroundResource(
            if (englishSelected) R.drawable.bg_btn_lang_selected else R.drawable.bg_suggestion_white_3d
        )
        btnLangFilipino.setBackgroundResource(
            if (englishSelected) R.drawable.bg_suggestion_white_3d else R.drawable.bg_btn_lang_selected
        )
        btnLangEnglish.setTextColor(
            if (englishSelected) Color.WHITE else Color.parseColor("#5796DB")
        )
        btnLangFilipino.setTextColor(
            if (englishSelected) Color.parseColor("#5796DB") else Color.WHITE
        )
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
                updateLanguageButtons()
            }
        }
        viewModel.voskService.onLevel = { level ->
            runOnUiThread {
                // Drives the real-microphone waveform. Harmless while idle: the
                // view swaps to the static line whenever recording is off.
                voiceWaveform.setLevel(level)
            }
        }
        viewModel.voskService.onTranscriptionResult = { text ->
            runOnUiThread {
                addRecognizedSpeech(text)
            }
        }
        viewModel.voskService.onRecordingStateChanged = { state ->
            runOnUiThread {
                when (state) {
                    VoskSTTService.RecordingState.RECORDING -> {
                        viewModel.isRecording.value = true
                        updateTapToSpeakUI(recording = true)
                    }
                    VoskSTTService.RecordingState.PROCESSING -> {}
                    VoskSTTService.RecordingState.DORMANT -> {
                        viewModel.isRecording.value = false
                        updateTapToSpeakUI(recording = false)
                    }
                }
            }
        }
    }

    /** Speech results land in the Build a sentence draft, like signed words do. */
    private fun addRecognizedSpeech(text: String) {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return
        sentenceWords.addAll(words)
        updateSentenceDraft()
    }

    /** Reflects the recording state on the Tap to speak / Stop button + waveform.
     *  Idle  = white 3D button, blue text, static line.
     *  Active = pressed #4A6FCC button, white text, live mic waveform. */
    private fun updateTapToSpeakUI(recording: Boolean) {
        btnTapToSpeak.text = if (recording) "Stop" else "Tap to speak"
        btnTapToSpeak.setBackgroundResource(
            if (recording) R.drawable.bg_btn_blue_pressed else R.drawable.bg_btn_white_3d
        )
        btnTapToSpeak.setTextColor(
            if (recording) Color.WHITE else Color.parseColor("#5796DB")
        )
        voiceWaveform.setActive(recording)
    }

    private var lastRenderedCount = 0

    private fun observeViewModel() {
        viewModel.entries.observe(this) { entries ->
            // A shrink (e.g. the RA 10173 conversation cleared on session end / peer
            // disconnect) must wipe rendered bubbles, or stale text would linger.
            if (entries.size < lastRenderedCount) {
                conversationContainer.removeAllViews()
                lastRenderedCount = 0
            }
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
            side = computeBubbleSide(entry),
            onDeafMessageSpeak = ::speakDeafMessage,
            deafBubbleColor = getDeafBubbleColor(),
            hearingBubbleColor = getHearingBubbleColor()
        )
        conversationContainer.addView(bubble)
    }

    /** Side of the conversation row a bubble occupies:
     *  - During a CONNECTED Dual Device session, alignment follows message ORIGIN so
     *    each phone shows its OWN messages on the RIGHT and the peer's on the LEFT.
     *  - Otherwise it keeps the original behavior: Deaf/FSL -> LEFT, Hearing/voice
     *    -> RIGHT. */
    private fun computeBubbleSide(entry: ConversationEntry): BubbleSide {
        if (viewModel.connectionState.value == ConnectionState.CONNECTED) {
            return if (entry.local) BubbleSide.RIGHT else BubbleSide.LEFT
        }
        return when (entry.sender) {
            SenderType.DEAF -> BubbleSide.LEFT
            SenderType.HEARING -> BubbleSide.RIGHT
        }
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
            AkaiNotification.short(this, "Text-to-speech unavailable on this device")
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
            AkaiNotification.short(this, "Install an offline text-to-speech voice to read messages aloud")
            return
        }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "deaf-message-${System.currentTimeMillis()}")
    }

    private fun switchToFSLMode() {
        if (viewModel.isRecording.value == true) viewModel.voskService.stopRecording()
        viewModel.switchToFSL()
        exitFingerspellMode()
        top3Panel.visibility = View.GONE
        updateLetterBuffer()
        // Restore the camera area and resume the camera.
        voiceOverlay.visibility = View.GONE
        btnFingerspell.visibility = View.VISIBLE
        if (!cameraRunning && allPermissionsGranted()) startCamera()
        // Reset button returns to its camera-mode blue 3D + white refresh icon.
        btnResetConversation.setBackgroundResource(R.drawable.bg_circle_blue_3d)
        btnResetConversation.imageTintList = null
        // Unified switch: only the selected (Camera) side gets the raised 3D look.
        btnFSLMode.setBackgroundResource(R.drawable.bg_mode_selected_3d)
        btnSpeechMode.setBackgroundResource(0)
        tvFSLStatus.setTextColor(Color.WHITE)
        tvSpeechStatus.setTextColor(akaiBlue())
        // Fixed result container: show recognition area, hide language controls.
        tvPrediction.visibility = View.VISIBLE
        voiceControlsPanel.visibility = View.GONE
        tvPrediction.text = "Show your hands..."
        applyModeIconTint(cameraActive = true, speechActive = false)
    }

    private fun switchToSpeechMode(onCameraStopped: (() -> Unit)? = null) {
        // Stop the camera so nothing runs underneath the blue voice area.
        stopCamera(onCameraStopped)
        viewModel.switchToSpeech()
        exitFingerspellMode()
        top3Panel.visibility = View.GONE
        updateLetterBuffer()
        // Blue voice area replaces the camera; no fingerspell/word-builder controls.
        voiceOverlay.visibility = View.VISIBLE
        btnFingerspell.visibility = View.GONE
        letterBufferPanel.visibility = View.GONE
        updateTapToSpeakUI(recording = false)
        // Reset button must stand out against the blue area: white circle + blue icon.
        btnResetConversation.setBackgroundResource(R.drawable.bg_circle_white_3d)
        btnResetConversation.imageTintList = ColorStateList.valueOf(Color.parseColor("#5796DB"))
        // Unified switch: only the selected (Voice) side gets the raised 3D look.
        btnFSLMode.setBackgroundResource(0)
        btnSpeechMode.setBackgroundResource(R.drawable.bg_mode_selected_3d)
        tvFSLStatus.setTextColor(akaiBlue())
        tvSpeechStatus.setTextColor(Color.WHITE)
        // Fixed result container: show language controls in the SAME fixed area.
        tvPrediction.visibility = View.GONE
        voiceControlsPanel.visibility = View.VISIBLE
        updateLanguageButtons()
        applyModeIconTint(cameraActive = false, speechActive = true)
    }

    /** UI-only: keeps the switcher icons legible on blue (selected) vs the theme background (unselected). */
    private fun applyModeIconTint(cameraActive: Boolean, speechActive: Boolean) {
        imgFSLIcon.imageTintList = ColorStateList.valueOf(
            if (cameraActive) Color.WHITE else akaiBlue()
        )
        imgSpeechIcon.imageTintList = ColorStateList.valueOf(
            if (speechActive) Color.WHITE else akaiBlue()
        )
    }

    /** AkAI brand blue — used for inactive/unselected text & icons in both themes. */
    private fun akaiBlue(): Int = getThemeColor(R.color.akai_blue)

    // =====================================================================
    //  FULL-SCREEN CAMERA <-> VOICE TRANSITION
    // =====================================================================

    /** Entry point for both switch segments. Same-mode re-selections keep the
     *  existing quiet re-switch (no overlay); real switches get the full flow. */
    private fun requestModeSwitch(target: ModeTarget) {
        if (modeSwitchInProgress) return
        val currentlyCamera = viewModel.isFSLMode.value ?: true
        val wantsCamera = target == ModeTarget.CAMERA
        if (currentlyCamera == wantsCamera) {
            if (wantsCamera) switchToFSLMode() else switchToSpeechMode()
            return
        }
        startModeTransition(target, wasCamera = currentlyCamera)
    }

    /** 1. Blue panel slides up from the bottom and covers the window.
     *  2. Once coverage is complete (+ a short pause) the actual mode switch runs
     *     and is waited on — the underlying UI never changes before the screen is
     *     fully blue. 3. Panel slides up once the new mode is actually ready. */
    private fun startModeTransition(target: ModeTarget, wasCamera: Boolean) {
        modeSwitchInProgress = true
        setModeSwitcherEnabled(false)

        val overlay = ModeTransitionOverlay(this)
        overlay.setTitle(
            if (target == ModeTarget.CAMERA) "Switching to Camera" else "Switching to Voice"
        )
        (window.decorView as ViewGroup).addView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        overlay.enter {
            mainHandler.postDelayed(
                { runModeSwitchBehindOverlay(target, wasCamera, overlay) },
                MODE_SWITCH_COVER_PAUSE_MS
            )
        }
        transitionStartMs = System.currentTimeMillis()
    }

    /** Performs the REAL mode change, but only now that the blue panel is
     *  completely covering the screen. Initialization still gates how long the
     *  panel stays up — nothing about `startCamera`/`switchTo*` semantics changed. */
    private fun runModeSwitchBehindOverlay(target: ModeTarget, wasCamera: Boolean, overlay: ModeTransitionOverlay) {
        when (target) {
            ModeTarget.VOICE -> {
                // Camera must be physically unbound and Vosk free of any
                // in-progress model load before Voice counts as ready.
                var cameraReleased = false
                switchToSpeechMode(onCameraStopped = { cameraReleased = true })
                pollUntilReady(
                    check = {
                        cameraReleased && !viewModel.voskService.isModelLoading
                    },
                    onReady = { completeModeTransition(overlay, success = true, restoreToCamera = wasCamera) },
                    onTimeout = { completeModeTransition(overlay, success = false, restoreToCamera = wasCamera) }
                )
            }
            ModeTarget.CAMERA -> {
                if (viewModel.isRecording.value == true) viewModel.voskService.stopRecording()
                switchToFSLMode()
                pollUntilReady(
                    check = { cameraRunning },
                    onReady = { completeModeTransition(overlay, success = true, restoreToCamera = wasCamera) },
                    onTimeout = { completeModeTransition(overlay, success = false, restoreToCamera = wasCamera) }
                )
            }
        }
    }

    /** Success: finish the loading bar. Failure: restore the previous working
     *  mode instead of leaving a half-switched screen. Either way the panel exits. */
    private fun completeModeTransition(overlay: ModeTransitionOverlay, success: Boolean, restoreToCamera: Boolean) {
        if (!success) {
            try {
                if (restoreToCamera) switchToFSLMode() else switchToSpeechMode()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            AkaiNotification.short(this, "Couldn't finish the mode switch")
        }
        // Guarantee a short minimum visibility so very fast switches never flash.
        val remaining = MODE_SWITCH_MIN_VISIBLE_MS - (System.currentTimeMillis() - transitionStartMs)
        if (remaining > 0) {
            mainHandler.postDelayed({ finishModeSwitch(overlay) }, remaining)
        } else {
            finishModeSwitch(overlay)
        }
    }

    /** Fill the bar, slide the panel up, remove it, unlock interaction. */
    private fun finishModeSwitch(overlay: ModeTransitionOverlay) {
        overlay.complete {
            overlay.exit {
                (window.decorView as ViewGroup).removeView(overlay)
                modeSwitchInProgress = false
                setModeSwitcherEnabled(true)
            }
        }
    }

    /** Polls every 100ms until [check] passes or [timeoutMs] elapses. Main thread. */
    private fun pollUntilReady(
        check: () -> Boolean,
        onReady: () -> Unit,
        onTimeout: () -> Unit,
        timeoutMs: Long = MODE_READY_TIMEOUT_MS
    ) {
        val started = System.currentTimeMillis()
        val poll = object : Runnable {
            override fun run() {
                if (check()) {
                    onReady()
                    return
                }
                if (System.currentTimeMillis() - started >= timeoutMs) {
                    onTimeout()
                    return
                }
                mainHandler.postDelayed(this, 100L)
            }
        }
        mainHandler.postDelayed(poll, 100L)
    }

    private fun setModeSwitcherEnabled(enabled: Boolean) {
        btnFSLMode.isEnabled = enabled
        btnSpeechMode.isEnabled = enabled
    }

    // =====================================================================
    //  TWO-DEVICE SESSION STATUS (session controls live ONLY on the dedicated
    //  "Connected to Session" page — nothing extra is drawn on the Main screen)
    // =====================================================================

    /** Reflects ViewModel connection state onto the Host/Join pages and the
     *  "Connected to Session" page. No persistent session banner is shown on the
     *  Main Activity. */
    private fun observeSessionState() {
        viewModel.connectionState.observe(this) { state ->
            val code = viewModel.sessionCode.value ?: ""
            when (state) {
                ConnectionState.IDLE -> {
                    // A dropped link / ended session must not leave the "Connected to
                    // Session" page covering the UI; slide it back to the main screen.
                    closeConnectedSessionPage()
                    setSessionTagVisible(false)
                }
                ConnectionState.HOSTING -> { /* host page drives its own UI */ setSessionTagVisible(false) }
                ConnectionState.JOINING -> {
                    joinSessionOverlay?.let { overlay ->
                        overlay.setStatusText("Searching for session $code…")
                        overlay.joinButton.isEnabled = false
                        overlay.showLoading()
                    }
                    setSessionTagVisible(false)
                }
                ConnectionState.CONNECTED -> {
                    hostSessionOverlay?.hideLoading()
                    joinSessionOverlay?.let { overlay ->
                        overlay.hideLoading()
                        overlay.joinButton.isEnabled = true
                    }
                    showConnectedSessionPage()
                    // Connected-session tag: top-right pill with the room code.
                    setSessionTagVisible(true)
                    sessionTagCode.text = code.ifBlank { "AK-0000" }
                }
                ConnectionState.ERROR -> {
                    hostSessionOverlay?.hideLoading()
                    joinSessionOverlay?.let { overlay ->
                        overlay.hideLoading()
                        overlay.joinButton.isEnabled = true
                        overlay.setStatusText(viewModel.syncError.value ?: "Could not connect")
                    }
                    setSessionTagVisible(false)
                }
                else -> setSessionTagVisible(false)
            }
        }
        viewModel.syncError.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                AkaiNotification.long(this, msg)
            }
        }
    }

    /** Shows/hides the connected-session tag on the main screen. */
    private fun setSessionTagVisible(visible: Boolean) {
        sessionTag.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** When a real connection completes, clear the Host/Join pages and pop the
     *  "Connected to Session" page. This is the ONE allowed animated transition
     *  here: it only fires on success (ConnectionState.CONNECTED), never on button
     *  press or validation. */
    private fun showConnectedSessionPage() {
        val host = hostSessionOverlay; val join = joinSessionOverlay
        hostSessionOverlay = null; joinSessionOverlay = null
        host?.exit { /* removed by the overlay itself */ }
        join?.exit { /* removed by the overlay itself */ }
        openConnectedSessionScreen()
    }

    // ---- Nearby runtime permissions (requested only when user opts into sync) ----
    private enum class PendingSyncAction { HOST, JOIN }
    private var pendingSyncAction: PendingSyncAction? = null

    private fun nearbyPermissions(): Array<String> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12+ : new Bluetooth runtime perms + nearby wifi
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            // Android 11 and below : location is required for BT/Wi-Fi scanning
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    private fun hasNearbyPermissions(): Boolean = nearbyPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNearbyPermissions(action: PendingSyncAction) {
        pendingSyncAction = action
        ActivityCompat.requestPermissions(this, nearbyPermissions(), REQUEST_NEARBY_PERMISSIONS)
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
                cameraRunning = true
            } catch (e: Exception) {
                e.printStackTrace()
                cameraRunning = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Fully releases the camera (used when entering Voice mode). [onStopped] fires
     *  once unbindAll() has actually completed — that is the signal the camera is free. */
    private fun stopCamera(onStopped: (() -> Unit)? = null) {
        cameraRunning = false
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onStopped?.invoke()
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
            buttons[i].setBackgroundResource(R.drawable.bg_suggestion_white_3d)
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
        if (buffer.isEmpty()) {
            tvLetterWord.text = ""
            letterBufferPanel.visibility = View.GONE
            return
        }
        // Letters render as a single block of bold uppercase blue text
        // (styled in XML) — no buttons, no backgrounds.
        tvLetterWord.text = buffer.joinToString("").uppercase()
        letterBufferPanel.visibility = View.VISIBLE
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Apply the user's saved theme preference at startup. Defaults to Dark Mode. */
    private fun applyThemeFromPreference() {
        val prefs = getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
        val saved = prefs.getInt(AppPreferences.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_YES)
        AppCompatDelegate.setDefaultNightMode(saved)
    }

    /** Resolve a theme-aware color resource to its actual int value. */
    private fun getThemeColor(colorResId: Int): Int {
        return ContextCompat.getColor(this, colorResId)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                if (allPermissionsGranted()) startCamera()
                else AkaiNotification.short(this, "Camera and microphone permissions required")
            }
            REQUEST_NEARBY_PERMISSIONS -> {
                val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (granted) {
                    // Permissions just granted — continue the action the user originally tapped.
                    when (pendingSyncAction) {
                        PendingSyncAction.HOST -> doStartSession()
                        PendingSyncAction.JOIN -> pendingJoinCode?.let { runJoin(it) }
                        null -> {}
                    }
                    pendingJoinCode = null
                } else {
                    AkaiNotification.long(this, "Two-device mode needs Bluetooth/Wi-Fi permissions")
                }
                pendingSyncAction = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        cameraExecutor.shutdown()
    }
}
