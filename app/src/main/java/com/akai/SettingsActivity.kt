package com.akai

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedCallback
import com.akai.data.AppPreferences
import com.akai.service.VoicePersona
import com.akai.service.VoicePersonaCatalog
import com.akai.ui.AkaiNotification
import com.akai.ui.ConversationBubbleWidget
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var backButton: TextView
    private lateinit var content: LinearLayout
    private lateinit var scrollScroll: ScrollView
    private var currentPage = Page.MAIN
    private var customPagePrefKey: String? = null
    private var customPageColor: Int = 0

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var previewingPersonaName: String? = null
    private var previewUtteranceIdsRemaining = mutableSetOf<String>()

    private enum class Page { MAIN, AUDIO, PERSONALIZATION, CUSTOM_COLOR }

    companion object {
        private val PREFS_NAME              = AppPreferences.PREFS_NAME
        private val KEY_DEAF_BUBBLE_COLOR   = AppPreferences.KEY_DEAF_BUBBLE_COLOR
        private val KEY_HEARING_BUBBLE_COLOR = AppPreferences.KEY_HEARING_BUBBLE_COLOR
        private val KEY_TTS_VOICE_PERSONA    = AppPreferences.KEY_TTS_VOICE_PERSONA
        private val KEY_THEME_MODE          = AppPreferences.KEY_THEME_MODE
        private val DEFAULT_DEAF_BUBBLE_COLOR    = AppPreferences.DEFAULT_DEAF_BUBBLE_COLOR
        private val DEFAULT_HEARING_BUBBLE_COLOR = AppPreferences.DEFAULT_HEARING_BUBBLE_COLOR

        // Instance-state keys: carry the exact Settings destination through the
        // Activity recreation that AppCompat performs when the theme switches.
        private const val STATE_PAGE            = "state_settings_page"
        private const val STATE_SCROLL          = "state_settings_scroll"
        private const val STATE_CUSTOM_PREF     = "state_custom_pref"
        private const val STATE_CUSTOM_COLOR    = "state_custom_color"

        private val PASTEL_COLORS = listOf(
            Color.parseColor("#A7C7E7"),
            Color.parseColor("#B8E6C1"),
            Color.parseColor("#D8B4E2"),
            Color.parseColor("#F4A7A3"),
            Color.parseColor("#FFD1A3"),
            Color.parseColor("#D6D6D6"),
            Color.WHITE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyThemeFromPreference()

        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (currentPage == Page.AUDIO) renderAudioPage()
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = onPreviewUtteranceFinished(utteranceId)
            @Deprecated("Deprecated in Java, still required to override")
            override fun onError(utteranceId: String?) = onPreviewUtteranceFinished(utteranceId)
        })

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBack()
                }
            }
        )
        buildBaseLayout()
        // Sync the system status/navigation bars with the active theme.
        SystemBarTheme.apply(this)

        if (savedInstanceState != null) {
            // The theme switch recreates the Activity via AppCompat. Instance state
            // carries the EXACT destination (page, scroll, custom-color context) so
            // the user stays on the same Settings section instead of being kicked
            // back to the Settings root.
            when (savedInstanceState.getString(STATE_PAGE)) {
                AppPreferences.PAGE_AUDIO -> renderAudioPage()
                AppPreferences.PAGE_PERSONALIZATION -> renderPersonalizationPage()
                AppPreferences.PAGE_CUSTOM_COLOR -> {
                    val prefKey = savedInstanceState.getString(STATE_CUSTOM_PREF)
                        ?: AppPreferences.KEY_HEARING_BUBBLE_COLOR
                    renderCustomColorPage(
                        prefKey = prefKey,
                        title = if (prefKey == AppPreferences.KEY_DEAF_BUBBLE_COLOR) "Custom Deaf Color" else "Custom Hearing Color",
                        initialColor = savedInstanceState.getInt(STATE_CUSTOM_COLOR, DEFAULT_HEARING_BUBBLE_COLOR)
                    )
                }
                else -> renderMainPage()
            }
            val savedScroll = savedInstanceState.getInt(STATE_SCROLL, 0)
            scrollScroll.post { scrollScroll.scrollTo(0, savedScroll) }
        } else {
            // First entry: fall back to the persisted page so the destination also
            // survives a process restart (e.g. the theme persisted across relaunch).
            when (prefs().getString(AppPreferences.KEY_SETTINGS_PAGE, AppPreferences.PAGE_MAIN)) {
                AppPreferences.PAGE_AUDIO -> renderAudioPage()
                AppPreferences.PAGE_PERSONALIZATION, AppPreferences.PAGE_CUSTOM_COLOR -> renderPersonalizationPage()
                else -> renderMainPage()
            }
            prefs().edit().remove(AppPreferences.KEY_SETTINGS_PAGE).apply()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(
            STATE_PAGE,
            when (currentPage) {
                Page.MAIN -> AppPreferences.PAGE_MAIN
                Page.AUDIO -> AppPreferences.PAGE_AUDIO
                Page.PERSONALIZATION -> AppPreferences.PAGE_PERSONALIZATION
                Page.CUSTOM_COLOR -> AppPreferences.PAGE_CUSTOM_COLOR
            }
        )
        if (::scrollScroll.isInitialized) {
            outState.putInt(STATE_SCROLL, scrollScroll.scrollY)
        }
        if (currentPage == Page.CUSTOM_COLOR) {
            customPagePrefKey?.let { outState.putString(STATE_CUSTOM_PREF, it) }
            outState.putInt(STATE_CUSTOM_COLOR, customPageColor)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }

    private fun applyThemeFromPreference() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val saved = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_YES)
        AppCompatDelegate.setDefaultNightMode(saved)
    }

    private fun isDarkMode(): Boolean {
        val mode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun bgColor(): Int = if (isDarkMode()) Color.parseColor("#121212") else Color.WHITE
    private fun surfaceColor(): Int = if (isDarkMode()) Color.parseColor("#1E1E1E") else Color.WHITE
    private fun textColorPrimary(): Int = if (isDarkMode()) Color.WHITE else Color.BLACK
    private fun rowBgColor(): Int = if (isDarkMode()) Color.parseColor("#263238") else Color.parseColor("#E8E8E8")
    private fun hintColor(): Int = if (isDarkMode()) Color.parseColor("#999999") else Color.parseColor("#888888")
    private fun selectedBgColor(): Int = if (isDarkMode()) Color.parseColor("#3949AB") else Color.parseColor("#5796DB")
    private fun akaiBlue(): Int = ContextCompat.getColor(this, R.color.akai_blue)
    private fun font(bold: Boolean): Typeface {
        val styled = if (bold) resources.getFont(R.font.poppins_bold) else resources.getFont(R.font.poppins_regular)
        return Typeface.create(styled, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun buildBaseLayout() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        // Edge-to-edge handling: never let the header sit under the status bar and
        // keep the last content row clear of the navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            windowInsets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(16), 0)
            setBackgroundColor(surfaceColor())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        }

        backButton = TextView(this).apply {
            val arrow = DrawableCompat.wrap(
                resources.getDrawable(R.drawable.ic_back_arrow, null)
            ).mutate()
            DrawableCompat.setTint(arrow, textColorPrimary())
            setCompoundDrawablesWithIntrinsicBounds(arrow, null, null, null)
            compoundDrawablePadding = dp(6)
            setPadding(dp(4), 0, dp(14), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { handleBack() }
        }

        titleView = TextView(this).apply {
            text = "Settings"
            textSize = 20f
            typeface = font(bold = true)
            setTextColor(textColorPrimary())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        header.addView(backButton)
        header.addView(titleView)

        scrollScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scrollScroll.addView(content)

        root.addView(header)
        root.addView(scrollScroll)
        setContentView(root)
    }

    private fun handleBack() {
        when (currentPage) {
            Page.MAIN -> finish()
            Page.AUDIO, Page.PERSONALIZATION -> renderMainPage()
            Page.CUSTOM_COLOR -> renderPersonalizationPage()
        }
    }

    private fun renderMainPage() {
        trackPage(Page.MAIN)
        currentPage = Page.MAIN
        titleView.text = "Settings"
        content.removeAllViews()
        content.addView(settingsRow("Audio") { renderAudioPage() })
        content.addView(settingsRow("Personalization and Theme") { renderPersonalizationPage() })
    }

    private fun renderAudioPage() {
        trackPage(Page.AUDIO)
        currentPage = Page.AUDIO
        titleView.text = "Audio"
        content.removeAllViews()

        val engine = tts
        if (!ttsReady || engine == null) {
            content.addView(hintText("Loading voices..."))
            return
        }

        val personas = VoicePersonaCatalog.build(engine)
        val selectedName = prefs().getString(KEY_TTS_VOICE_PERSONA, null) ?: "Alex"

        content.addView(sectionTitle("Voices"))
        if (personas.isEmpty()) {
            content.addView(hintText("No offline voices found on your device."))
        } else {
            content.addView(hintText("Tap play to listen, tap the name to choose it."))
            personas.forEach { persona -> content.addView(personaRow(persona, persona.name == selectedName)) }
        }
    }

    private fun personaRow(persona: VoicePersona, selected: Boolean): LinearLayout {
        val isPlaying = previewingPersonaName == persona.name
        val nameColor = if (selected) Color.WHITE else textColorPrimary()
        val controlColor = if (selected) Color.WHITE else akaiBlue()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = roundedRect(if (selected) selectedBgColor() else rowBgColor(), 6f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply { bottomMargin = dp(10) }

            addView(TextView(this@SettingsActivity).apply {
                text = if (selected) "${persona.name} \u2713" else persona.name
                textSize = 16f
                typeface = font(bold = false)
                setTextColor(nameColor)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, 0, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs().edit().putString(KEY_TTS_VOICE_PERSONA, persona.name).apply()
                    renderAudioPage()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            })
            addView(ImageButton(this@SettingsActivity).apply {
                setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                imageTintList = ColorStateList.valueOf(controlColor)
                background = roundedRect(Color.TRANSPARENT, 8f)
                setPadding(dp(7), dp(7), dp(7), dp(7))
                contentDescription = if (isPlaying) "Pause voice preview" else "Play ${persona.name} preview"
                isClickable = true
                isFocusable = true
                setOnClickListener { togglePreview(persona) }
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                    marginEnd = dp(6)
                }
            })
        }
    }

    private fun togglePreview(persona: VoicePersona) {
        val engine = tts ?: return
        if (previewingPersonaName == persona.name) {
            engine.stop()
            finishPreview()
            return
        }
        previewingPersonaName = persona.name

        val enId = "voice-preview-${persona.name}-en"
        engine.language = Locale.US
        engine.voice = persona.englishVoice
        engine.setPitch(1.0f)
        engine.setSpeechRate(1.0f)
        engine.speak("Hello, this is ${persona.name}.", TextToSpeech.QUEUE_FLUSH, null, enId)
        val queuedIds = mutableSetOf(enId)

        val filipinoVoice = persona.filipinoVoice
        if (filipinoVoice != null) {
            val filId = "voice-preview-${persona.name}-fil"
            engine.language = Locale("fil", "PH")
            engine.voice = filipinoVoice
            engine.setPitch(1.0f)
            engine.setSpeechRate(1.0f)
            engine.speak("Kumusta ka?", TextToSpeech.QUEUE_ADD, null, filId)
            queuedIds.add(filId)
        }

        previewUtteranceIdsRemaining = queuedIds
        renderAudioPage()
    }

    private fun onPreviewUtteranceFinished(utteranceId: String?) {
        if (utteranceId == null) return
        previewUtteranceIdsRemaining.remove(utteranceId)
        if (previewUtteranceIdsRemaining.isEmpty()) finishPreview()
    }

    private fun finishPreview() {
        previewingPersonaName = null
        previewUtteranceIdsRemaining.clear()
        runOnUiThread { if (currentPage == Page.AUDIO) renderAudioPage() }
    }

    private fun hintText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = font(bold = false)
            setTextColor(hintColor())
            setPadding(0, 0, 0, dp(10))
        }
    }

    private fun renderPersonalizationPage() {
        trackPage(Page.PERSONALIZATION)
        currentPage = Page.PERSONALIZATION
        titleView.text = "Personalization and Theme"
        content.removeAllViews()

        // Theme selection
        content.addView(sectionTitle("Theme"))
        content.addView(themeSelectionRow())
        content.addView(hintText("Choose your preferred appearance. Dark Mode is the default."))

        content.addView(previewBlock())
        content.addView(sectionTitle("Deaf Bubble"))
        content.addView(colorOptionsRow(KEY_DEAF_BUBBLE_COLOR, DEFAULT_DEAF_BUBBLE_COLOR))
        content.addView(sectionTitle("Hearing Bubble"))
        content.addView(colorOptionsRow(KEY_HEARING_BUBBLE_COLOR, DEFAULT_HEARING_BUBBLE_COLOR))
    }

    /**
     * Fixed-size segmented Light/Dark switch that mirrors the Home screen's Camera /
     * Voice switcher visuals: flat themed pill + blue outline, selected segment raised
     * 3D blue, unselected flat with blue label. No loading, no manual recreate — the
     * theme applies instantly via AppCompat (the Activity recreates on its own).
     */
    private fun themeSelectionRow(): LinearLayout {
        val isDark = prefs().getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_YES) == AppCompatDelegate.MODE_NIGHT_YES

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_mode_container)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply { bottomMargin = dp(4) }
            foreground = null
        }

        container.addView(modeSegment("LIGHT MODE", selected = !isDark) { selectTheme(dark = false) })
        container.addView(modeSegment("DARK MODE", selected = isDark) { selectTheme(dark = true) })
        return container
    }

    private fun modeSegment(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            letterSpacing = 0.4f
            typeface = font(bold = true)
            setTextColor(if (selected) Color.WHITE else akaiBlue())
            gravity = android.view.Gravity.CENTER
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = if (selected) ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_mode_selected_3d) else null
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            }
        }
    }

    private fun selectTheme(dark: Boolean) {
        val newMode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (prefs().getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_YES) == newMode) return
        prefs().edit().putInt(KEY_THEME_MODE, newMode).apply()
        // No manual recreate() and no loading: AppCompat recreates the Activity
        // automatically and onCreate() restores the page the user was on.
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    private fun renderCustomColorPage(prefKey: String, title: String, initialColor: Int) {
        trackPage(Page.CUSTOM_COLOR)
        currentPage = Page.CUSTOM_COLOR
        titleView.text = title
        content.removeAllViews()

        // Keep the custom-color context in fields so the theme-switch recreation
        // can put the user back on the exact same editing screen.
        customPagePrefKey = prefKey
        customPageColor = initialColor

        val previewContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        fun updateCustomPreview() {
            previewContainer.removeAllViews()
            previewContainer.addView(previewBlock { pref ->
                if (pref == prefKey) customPageColor else prefs().getInt(pref, defaultFor(pref))
            })
        }
        updateCustomPreview()

        val wheel = ColorWheelView(this).apply {
            setColor(initialColor)
            onColorChanged = { color ->
                customPageColor = color
                updateCustomPreview()
            }
        }
        val brightness = SeekBar(this).apply {
            max = 100
            progress = 100
            setPadding(0, dp(12), 0, dp(12))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val hsv = FloatArray(3)
                    Color.colorToHSV(customPageColor, hsv)
                    hsv[2] = progress.coerceAtLeast(8) / 100f
                    customPageColor = Color.HSVToColor(hsv)
                    wheel.setColor(customPageColor)
                    updateCustomPreview()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val save = settingsRow("Save Color") {
            prefs().edit().putInt(prefKey, customPageColor).apply()
            AkaiNotification.short(this@SettingsActivity, "Color saved")
            renderPersonalizationPage()
        }

        content.addView(previewContainer)
        content.addView(wheel, LinearLayout.LayoutParams(dp(260), dp(260)).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            topMargin = dp(16)
            bottomMargin = dp(8)
        })
        content.addView(sectionTitle("Brightness"))
        content.addView(brightness)
        content.addView(save)
    }

    private fun settingsRow(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 16f
            typeface = font(bold = false)
            setTextColor(textColorPrimary())
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            background = roundedRect(rowBgColor(), 6f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = font(bold = true)
            setTextColor(akaiBlue())
            setPadding(0, dp(16), 0, dp(8))
        }
    }

    private fun colorOptionsRow(prefKey: String, defaultColor: Int): HorizontalScrollView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(4))
        }
        PASTEL_COLORS.forEach { color ->
            row.addView(colorCircle(color, prefs().getInt(prefKey, defaultColor) == color) {
                prefs().edit().putInt(prefKey, color).apply()
                renderPersonalizationPage()
            })
        }
        row.addView(customCircle {
            renderCustomColorPage(prefKey, if (prefKey == KEY_DEAF_BUBBLE_COLOR) "Custom Deaf Color" else "Custom Hearing Color", prefs().getInt(prefKey, defaultColor))
        })

        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun colorCircle(color: Int, selected: Boolean, onClick: () -> Unit): View {
        return View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(dp(if (selected) 3 else 1), if (selected) akaiBlue() else Color.parseColor("#555555"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginEnd = dp(10)
            }
        }
    }

    private fun customCircle(onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = "+"
            textSize = 22f
            typeface = font(bold = true)
            setTextColor(akaiBlue())
            gravity = android.view.Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#A7C7E7"),
                    Color.parseColor("#D8B4E2"),
                    Color.parseColor("#FFD1A3")
                )
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), akaiBlue())
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginEnd = dp(10)
            }
        }
    }

    private fun previewBlock(colorProvider: ((String) -> Int)? = null): LinearLayout {
        val deafColor = colorProvider?.invoke(KEY_DEAF_BUBBLE_COLOR)
            ?: prefs().getInt(KEY_DEAF_BUBBLE_COLOR, DEFAULT_DEAF_BUBBLE_COLOR)
        val hearingColor = colorProvider?.invoke(KEY_HEARING_BUBBLE_COLOR)
            ?: prefs().getInt(KEY_HEARING_BUBBLE_COLOR, DEFAULT_HEARING_BUBBLE_COLOR)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            // rowBgColor (not the light surface) so a WHITE bubble stays visible on
            // the preview card in Light Mode; a thin border frames the card.
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(rowBgColor())
                setStroke(
                    dp(1),
                    if (isDarkMode()) Color.parseColor("#3A3A3A") else Color.parseColor("#DDDDDD")
                )
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
            addView(previewBubble("Hello", deafColor, true))
            addView(previewBubble("Nice to meet you", hearingColor, false))
        }
    }

    private fun previewBubble(text: String, color: Int, deaf: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (deaf) android.view.Gravity.START else android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
        row.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            typeface = font(bold = false)
            setTextColor(readableTextColor(color))
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = bubbleDrawable(color, deaf)
        })
        return row
    }

    private fun bubbleDrawable(color: Int, deaf: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            val small = dp(4).toFloat()
            val large = dp(16).toFloat()
            cornerRadii = if (deaf) {
                floatArrayOf(small, small, large, large, large, large, large, large)
            } else {
                floatArrayOf(large, large, small, small, large, large, large, large)
            }
        }
    }

    private fun roundedRect(color: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = radiusDp * resources.displayMetrics.density
            if (color != Color.TRANSPARENT) {
                setColor(color)
            } else {
                setColor(Color.TRANSPARENT)
            }
        }
    }

    private fun trackPage(page: Page) {
        val key = when (page) {
            Page.MAIN -> AppPreferences.PAGE_MAIN
            Page.AUDIO -> AppPreferences.PAGE_AUDIO
            Page.PERSONALIZATION -> AppPreferences.PAGE_PERSONALIZATION
            Page.CUSTOM_COLOR -> AppPreferences.PAGE_CUSTOM_COLOR
        }
        prefs().edit().putString(AppPreferences.KEY_SETTINGS_PAGE, key).apply()
    }

    private fun prefs(): SharedPreferences {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private fun defaultFor(prefKey: String): Int {
        return if (prefKey == KEY_DEAF_BUBBLE_COLOR) DEFAULT_DEAF_BUBBLE_COLOR else DEFAULT_HEARING_BUBBLE_COLOR
    }

    private fun readableTextColor(backgroundColor: Int): Int =
        ConversationBubbleWidget.readableTextColor(backgroundColor)

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private class ColorWheelView(context: android.content.Context) : View(context) {
        var onColorChanged: ((Int) -> Unit)? = null

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var wheelBitmap: Bitmap? = null
        private var hue = 0f
        private var saturation = 1f
        private var value = 1f

        fun setColor(color: Int) {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hue = hsv[0]
            saturation = hsv[1]
            value = hsv[2]
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            wheelBitmap = buildWheelBitmap(w, h)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            wheelBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

            val radius = (width.coerceAtMost(height) / 2f) - 4f
            val angle = Math.toRadians(hue.toDouble())
            val markerRadius = saturation * radius
            val x = width / 2f + (Math.cos(angle) * markerRadius).toFloat()
            val y = height / 2f + (Math.sin(angle) * markerRadius).toFloat()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.WHITE
            canvas.drawCircle(x, y, 12f, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE) return true
            val centerX = width / 2f
            val centerY = height / 2f
            val dx = event.x - centerX
            val dy = event.y - centerY
            val radius = (width.coerceAtMost(height) / 2f) - 4f
            saturation = (kotlin.math.sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
            hue = ((Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f)
            onColorChanged?.invoke(Color.HSVToColor(floatArrayOf(hue, saturation, value)))
            invalidate()
            return true
        }

        private fun buildWheelBitmap(w: Int, h: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val centerX = w / 2f
            val centerY = h / 2f
            val radius = (w.coerceAtMost(h) / 2f) - 4f
            for (x in 0 until w) {
                for (y in 0 until h) {
                    val dx = x - centerX
                    val dy = y - centerY
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (distance <= radius) {
                        val hue = ((Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f)
                        val saturation = (distance / radius).coerceIn(0f, 1f)
                        bitmap.setPixel(x, y, Color.HSVToColor(floatArrayOf(hue, saturation, 1f)))
                    } else {
                        bitmap.setPixel(x, y, Color.TRANSPARENT)
                    }
                }
            }
            return bitmap
        }
    }
}