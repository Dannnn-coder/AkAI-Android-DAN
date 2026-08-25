package com.akai

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.akai.data.AppPreferences
import com.akai.service.VoicePersona
import com.akai.service.VoicePersonaCatalog
import com.akai.ui.ConversationBubbleWidget
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var backButton: TextView
    private lateinit var content: LinearLayout
    private var currentPage = Page.MAIN

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
        private val DEFAULT_DEAF_BUBBLE_COLOR    = AppPreferences.DEFAULT_DEAF_BUBBLE_COLOR
        private val DEFAULT_HEARING_BUBBLE_COLOR = AppPreferences.DEFAULT_HEARING_BUBBLE_COLOR

        private val PASTEL_COLORS = listOf(
            Color.parseColor("#A7C7E7"),
            Color.parseColor("#B8E6C1"),
            Color.parseColor("#D8B4E2"),
            Color.parseColor("#F4A7A3"),
            Color.parseColor("#FFD1A3"),
            Color.parseColor("#D6D6D6")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        renderMainPage()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }

    private fun buildBaseLayout() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(16), 0)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        }

        backButton = TextView(this).apply {
            text = "<"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(dp(10), 0, dp(14), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { handleBack() }
        }

        titleView = TextView(this).apply {
            text = "Settings"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        header.addView(backButton)
        header.addView(titleView)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scroll.addView(content)

        root.addView(header)
        root.addView(scroll)
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
        currentPage = Page.MAIN
        titleView.text = "Settings"
        content.removeAllViews()
        content.addView(settingsRow("Audio") { renderAudioPage() })
        content.addView(settingsRow("Personalization") { renderPersonalizationPage() })
    }

    private fun renderAudioPage() {
        currentPage = Page.AUDIO
        titleView.text = "Audio"
        content.removeAllViews()

        val engine = tts
        if (!ttsReady || engine == null) {
            content.addView(hintText("Loading voices…"))
            return
        }

        val personas = VoicePersonaCatalog.build(engine)
        val selectedName = prefs().getString(KEY_TTS_VOICE_PERSONA, null) ?: "Alex"

        content.addView(sectionTitle("Voices"))
        if (personas.isEmpty()) {
            content.addView(hintText("No offline voices found on your device."))
        } else {
            content.addView(hintText("Tap ▶ to listen, tap the name to choose it."))
            personas.forEach { persona -> content.addView(personaRow(persona, persona.name == selectedName)) }
        }
    }

    /** Tapping the name selects the persona; tapping ▶/❙❙ only previews it, without selecting. */
    private fun personaRow(persona: VoicePersona, selected: Boolean): LinearLayout {
        val isPlaying = previewingPersonaName == persona.name
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = roundedRect(if (selected) Color.parseColor("#3949AB") else Color.parseColor("#263238"), 6f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            ).apply { bottomMargin = dp(10) }

            addView(TextView(this@SettingsActivity).apply {
                text = if (selected) "${persona.name} ✓" else persona.name
                textSize = 16f
                setTextColor(Color.WHITE)
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
            addView(TextView(this@SettingsActivity).apply {
                text = if (isPlaying) "❙❙" else "▶"
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = android.view.Gravity.CENTER
                setPadding(dp(14), dp(14), dp(14), dp(14))
                isClickable = true
                isFocusable = true
                setOnClickListener { togglePreview(persona) }
            })
        }
    }

    /** Plays the English side of the persona, then the Filipino side (if paired), back to back. */
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
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, dp(10))
        }
    }

    private fun renderPersonalizationPage() {
        currentPage = Page.PERSONALIZATION
        titleView.text = "Personalization"
        content.removeAllViews()
        content.addView(previewBlock())
        content.addView(sectionTitle("Deaf Bubble"))
        content.addView(colorOptionsRow(KEY_DEAF_BUBBLE_COLOR, DEFAULT_DEAF_BUBBLE_COLOR))
        content.addView(sectionTitle("Hearing Bubble"))
        content.addView(colorOptionsRow(KEY_HEARING_BUBBLE_COLOR, DEFAULT_HEARING_BUBBLE_COLOR))
    }

    private fun renderCustomColorPage(prefKey: String, title: String, initialColor: Int) {
        currentPage = Page.CUSTOM_COLOR
        titleView.text = title
        content.removeAllViews()

        var selectedColor = initialColor
        val previewContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        fun updateCustomPreview() {
            previewContainer.removeAllViews()
            previewContainer.addView(previewBlock { pref ->
                if (pref == prefKey) selectedColor else prefs().getInt(pref, defaultFor(pref))
            })
        }
        updateCustomPreview()

        val wheel = ColorWheelView(this).apply {
            setColor(initialColor)
            onColorChanged = { color ->
                selectedColor = color
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
                    Color.colorToHSV(selectedColor, hsv)
                    hsv[2] = progress.coerceAtLeast(8) / 100f
                    selectedColor = Color.HSVToColor(hsv)
                    wheel.setColor(selectedColor)
                    updateCustomPreview()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val save = settingsRow("Save Color") {
            prefs().edit().putInt(prefKey, selectedColor).apply()
            Toast.makeText(this, "Color saved", Toast.LENGTH_SHORT).show()
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
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            background = roundedRect(Color.parseColor("#263238"), 6f)
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
            setTextColor(Color.parseColor("#BDBDBD"))
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
                setStroke(dp(if (selected) 3 else 1), if (selected) Color.WHITE else Color.parseColor("#555555"))
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
            setTextColor(Color.WHITE)
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
                setStroke(dp(1), Color.parseColor("#555555"))
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
            background = roundedRect(Color.parseColor("#1E1E1E"), 6f)
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
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
        }
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
