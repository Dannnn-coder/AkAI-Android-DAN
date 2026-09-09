package com.akai

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.akai.data.AppPreferences

/**
 * Offline FSL Dictionary — displays the vocabulary actually supported by the
 * AkAI model (parsed from actions.txt). Organized into Letters (A-Z) and
 * Words/Phrases categories with real-time search.
 */
class DictionaryActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var backButton: TextView
    private lateinit var searchInput: EditText
    private lateinit var dictionaryContainer: LinearLayout
    private lateinit var noResultsText: TextView

    private val allEntries = mutableListOf<DictionaryEntry>()

    data class DictionaryEntry(
        val key: String,
        val displayName: String,
        val category: String,
        val description: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeFromPreference()
        loadVocabulary()
        buildUI()
        // Sync the system status/navigation bars with the active theme.
        SystemBarTheme.apply(this)
    }

    private fun applyThemeFromPreference() {
        val prefs = getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
        val saved = prefs.getInt(AppPreferences.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_YES)
        AppCompatDelegate.setDefaultNightMode(saved)
    }

    private fun isDarkMode(): Boolean {
        val mode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun bgColor(): Int = if (isDarkMode()) Color.parseColor("#121212") else Color.WHITE
    private fun surfaceColor(): Int = if (isDarkMode()) Color.parseColor("#1E1E1E") else Color.WHITE
    private fun textColorPrimary(): Int = if (isDarkMode()) Color.WHITE else Color.BLACK
    private fun textColorSecondary(): Int = if (isDarkMode()) Color.parseColor("#999999") else Color.parseColor("#888888")
    private fun rowBgColor(): Int = if (isDarkMode()) Color.parseColor("#263238") else Color.parseColor("#F0F0F0")
    private fun searchBgColor(): Int = if (isDarkMode()) Color.parseColor("#2A2A2A") else Color.parseColor("#F0F0F0")

    private fun loadVocabulary() {
        val actions = assets.open("actions.txt").bufferedReader().readLines()
        for (action in actions) {
            val trimmed = action.trim()
            if (trimmed.isBlank()) continue

            val entry = when {
                trimmed.startsWith("letter_") -> {
                    val letter = trimmed.removePrefix("letter_").uppercase()
                    DictionaryEntry(
                        key = trimmed,
                        displayName = letter,
                        category = "Letters",
                        description = "Fingerspelling letter: $letter"
                    )
                }
                trimmed == "again" -> DictionaryEntry(trimmed, "Again", "Words & Phrases", "Sign for 'again' / 'ulit'")
                trimmed == "deaf" -> DictionaryEntry(trimmed, "Deaf", "Words & Phrases", "Sign for 'deaf' / 'bingi'")
                trimmed == "dont_understand" -> DictionaryEntry(trimmed, "Don't Understand", "Words & Phrases", "Sign for 'don't understand' / 'hindi ko naintindihan'")
                trimmed == "good_afternoon" -> DictionaryEntry(trimmed, "Good Afternoon", "Words & Phrases", "Sign for 'good afternoon' / 'magandang hapon'")
                trimmed == "good_evening" -> DictionaryEntry(trimmed, "Good Evening", "Words & Phrases", "Sign for 'good evening' / 'magandang gabi'")
                trimmed == "good_morning" -> DictionaryEntry(trimmed, "Good Morning", "Words & Phrases", "Sign for 'good morning' / 'magandang umaga'")
                trimmed == "goodbye" -> DictionaryEntry(trimmed, "Goodbye", "Words & Phrases", "Sign for 'goodbye' / 'paalam'")
                trimmed == "hello" -> DictionaryEntry(trimmed, "Hello", "Words & Phrases", "Sign for 'hello' / 'kumusta'")
                trimmed == "how_are_you" -> DictionaryEntry(trimmed, "How Are You?", "Words & Phrases", "Sign for 'how are you?' / 'kumusta ka?'")
                trimmed == "i_am_from" -> DictionaryEntry(trimmed, "I Am From", "Words & Phrases", "Sign for 'I am from' / 'galing ako sa'")
                trimmed == "i_me_my" -> DictionaryEntry(trimmed, "I / Me / My", "Words & Phrases", "Sign for 'I', 'me', or 'my' / 'ako', 'akin'")
                trimmed == "im_fine" -> DictionaryEntry(trimmed, "I'm Fine", "Words & Phrases", "Sign for 'I am fine' / 'ayos lang ako'")
                trimmed == "my_name_is" -> DictionaryEntry(trimmed, "My Name Is", "Words & Phrases", "Sign for 'my name is' / 'ang pangalan ko'")
                trimmed == "nice_to_meet_you" -> DictionaryEntry(trimmed, "Nice to Meet You", "Words & Phrases", "Sign for 'nice to meet you' / 'makilala kita'")
                trimmed == "no" -> DictionaryEntry(trimmed, "No", "Words & Phrases", "Sign for 'no' / 'hindi'")
                trimmed == "see_you_tomorrow" -> DictionaryEntry(trimmed, "See You Tomorrow", "Words & Phrases", "Sign for 'see you tomorrow' / 'hanggang bukas'")
                trimmed == "slow" -> DictionaryEntry(trimmed, "Slow", "Words & Phrases", "Sign for 'slow' / 'mabagal'")
                trimmed == "thank_you" -> DictionaryEntry(trimmed, "Thank You", "Words & Phrases", "Sign for 'thank you' / 'salamat'")
                trimmed == "understand" -> DictionaryEntry(trimmed, "Understand", "Words & Phrases", "Sign for 'understand' / 'naintindihan'")
                trimmed == "what_is_your_name" -> DictionaryEntry(trimmed, "What Is Your Name?", "Words & Phrases", "Sign for 'what is your name?' / 'ano pangalan mo?'")
                trimmed == "where_live" -> DictionaryEntry(trimmed, "Where Do You Live?", "Words & Phrases", "Sign for 'where do you live?' / 'saan ka nakatira?'")
                trimmed == "yes" -> DictionaryEntry(trimmed, "Yes", "Words & Phrases", "Sign for 'yes' / 'oo'")
                trimmed == "you" -> DictionaryEntry(trimmed, "You", "Words & Phrases", "Sign for 'you' / 'ikaw'")
                trimmed == "you_are_welcome" -> DictionaryEntry(trimmed, "You're Welcome", "Words & Phrases", "Sign for 'you're welcome' / 'walang anuman'")
                else -> DictionaryEntry(
                    trimmed,
                    trimmed.replace("_", " ").replaceFirstChar { it.uppercase() },
                    "Words & Phrases",
                    "Sign for '${trimmed.replace("_", " ")}'"
                )
            }
            allEntries.add(entry)
        }
    }

    private fun buildUI() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            setOnClickListener { finish() }
        }

        titleView = TextView(this).apply {
            text = "Dictionary"
            textSize = 20f
            setTextColor(textColorPrimary())
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        header.addView(backButton)
        header.addView(titleView)

        // Search bar
        val searchContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
            setBackgroundColor(bgColor())
        }

        searchInput = EditText(this).apply {
            hint = "Search FSL vocabulary..."
            textSize = 15f
            setTextColor(textColorPrimary())
            setHintTextColor(textColorSecondary())
            background = GradientDrawable().apply {
                setColor(searchBgColor())
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), if (isDarkMode()) Color.parseColor("#3A3A3A") else Color.parseColor("#DDDDDD"))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    filterDictionary(s?.toString() ?: "")
                }
            })
        }
        searchContainer.addView(searchInput)

        // Dictionary content area
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            isFillViewport = true
        }
        dictionaryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }

        noResultsText = TextView(this).apply {
            text = "No matching signs found."
            textSize = 14f
            setTextColor(textColorSecondary())
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(100)
            ).apply { topMargin = dp(40) }
        }

        scroll.addView(dictionaryContainer)

        root.addView(header)
        root.addView(searchContainer)
        root.addView(noResultsText)
        root.addView(scroll)

        setContentView(root)

        // Apply edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        renderEntries(allEntries)
    }

    private fun filterDictionary(query: String) {
        if (query.isBlank()) {
            renderEntries(allEntries)
            return
        }
        // Search friendly, human-readable fields only — the internal identifier is
        // never exposed to (or searchable by) the user.
        val lower = query.lowercase()
        val filtered = allEntries.filter { entry ->
            entry.displayName.lowercase().contains(lower) ||
                entry.description.lowercase().contains(lower) ||
                entry.category.lowercase().contains(lower)
        }
        renderEntries(filtered)
    }

    private fun renderEntries(entries: List<DictionaryEntry>) {
        dictionaryContainer.removeAllViews()
        noResultsText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        if (entries.isEmpty()) return

        // Group by category
        val grouped = entries.groupBy { it.category }
        val categoryOrder = listOf("Letters", "Words & Phrases")

        for (category in categoryOrder) {
            val items = grouped[category] ?: continue
            if (items.isEmpty()) continue

            // Category header
            val headerView = TextView(this).apply {
                text = category
                textSize = 14f
                setTextColor(androidx.core.content.ContextCompat.getColor(this@DictionaryActivity, com.akai.R.color.akai_blue))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(8))
            }
            dictionaryContainer.addView(headerView)

            // Alphabetically sort within category
            val sorted = if (category == "Letters") {
                items.sortedBy { it.displayName }
            } else {
                items.sortedBy { it.displayName.lowercase() }
            }

            if (category == "Letters") {
                // Letters render as a centered tiled grid — 6 per row, the final
                // partial row centered. Cell width stays identical across rows.
                sorted.chunked(6).forEach { cells ->
                    dictionaryContainer.addView(createLetterGridRow(cells))
                }
            } else {
                for (entry in sorted) {
                    dictionaryContainer.addView(createEntryRow(entry))
                }
            }
        }
    }

    /** A row of the letter grid. Partial rows are centered with equal-width cells. */
    private fun createLetterGridRow(cells: List<DictionaryEntry>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        if (cells.size < 6) {
            // Weight spacers mirror the leftover slots so the remaining letters sit
            // centered while each cell keeps the same 1/6 width as full rows.
            val sideWeight = (6f - cells.size) / 2f
            row.addView(letterSpacer(sideWeight))
            cells.forEach { row.addView(letterCell(it, 1f)) }
            row.addView(letterSpacer(sideWeight))
        } else {
            cells.forEach { row.addView(letterCell(it, 1f)) }
        }
        return row
    }

    private fun letterSpacer(weight: Float): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, weight)
        }
    }

    /** A square-ish letter tile showing ONLY the letter (no internal identifiers). */
    private fun letterCell(entry: DictionaryEntry, weight: Float): TextView {
        return TextView(this).apply {
            text = entry.displayName
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(textColorPrimary())
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(rowBgColor())
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(54), weight).apply {
                leftMargin = dp(3)
                rightMargin = dp(3)
            }
        }
    }

    private fun createEntryRow(entry: DictionaryEntry): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(rowBgColor())
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }

            // Word/phrase name
            addView(TextView(this@DictionaryActivity).apply {
                text = entry.displayName
                textSize = 16f
                setTextColor(textColorPrimary())
                setTypeface(typeface, Typeface.BOLD)
            })

            // Description / "Sign for ..." translation
            addView(TextView(this@DictionaryActivity).apply {
                text = entry.description
                textSize = 13f
                setTextColor(textColorSecondary())
                setPadding(0, dp(4), 0, 0)
                maxLines = 2
            })
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
