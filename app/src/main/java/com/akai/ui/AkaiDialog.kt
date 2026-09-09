package com.akai.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.akai.R

/**
 * Reusable AkAI modal dialog — a themed, centered text box that BLOCKS the whole
 * screen until one of its buttons is pressed. The full-screen overlay consumes
 * every touch (tapping outside does nothing), so nothing behind it can be hit.
 * Used for the first-time onboarding question and the reset confirmation.
 */
object AkaiDialog {

    /**
     * A dialog action button.
     *
     * [textOnly] renders the label as a plain text link with NO background / depth
     * (the same look the step-by-step tutorial uses for its Skip control). All
     * other buttons use the reusable brand-blue 3D pill.
     */
    data class Button(val label: String, val textOnly: Boolean = false, val onClick: () -> Unit = {})

    private var activeHost: ViewGroup? = null
    private var activeRoot: View? = null

    fun show(
        activity: Activity,
        title: String? = null,
        message: String,
        buttons: List<Button> = emptyList()
    ) {
        val host = activity.window.decorView as ViewGroup
        val dark = isDarkMode(activity)
        val density = activity.resources.displayMetrics.density
        dismissNow()

        val root = FrameLayout(activity).apply {
            // Full-screen touch sink: every tap outside the card is swallowed here,
            // which is what makes the dialog fully modal.
            setOnTouchListener { _, _ -> true }
            setBackgroundColor(if (dark) Color.parseColor("#B3000000") else Color.parseColor("#99000000"))
        }

        val card = buildCard(activity, dark, density, title, message, buttons)
        root.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            leftMargin = (24f * density).toInt()
            rightMargin = (24f * density).toInt()
        })

        // Keep clear of the status/navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        host.addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        card.scaleY = 0.92f
        card.alpha = 0f
        card.animate()
            .scaleY(1f)
            .alpha(1f)
            .setDuration(200L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        activeHost = host
        activeRoot = root
    }

    private fun buildCard(
        activity: Activity,
        dark: Boolean,
        density: Float,
        title: String?,
        message: String,
        buttons: List<Button>
    ): View {
        val surface = if (dark) Color.parseColor("#1E1E1E") else Color.WHITE
        val depth   = if (dark) Color.parseColor("#000000") else Color.parseColor("#C9D6F0")
        val titleText = if (dark) Color.WHITE else Color.parseColor("#2196F3")
        val bodyText  = if (dark) Color.parseColor("#E0E0E0") else Color.parseColor("#1F1F1F")

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = themedCardBackground(surface, depth, density)
            setPadding(
                (20f * density).toInt(),
                (18f * density).toInt(),
                (20f * density).toInt(),
                (18f * density).toInt()
            )
        }

        if (!title.isNullOrBlank()) {
            card.addView(TextView(activity).apply {
                text = title
                textSize = 18f
                gravity = Gravity.CENTER
                typeface = poppins(activity, bold = true)
                setTextColor(titleText)
                setLineSpacing(0f, 1.05f)
            })
        }

        card.addView(TextView(activity).apply {
            text = message
            textSize = 14f
            gravity = if (title.isNullOrBlank()) Gravity.CENTER else Gravity.CENTER
            typeface = poppins(activity, bold = false)
            setTextColor(bodyText)
            setLineSpacing(0f, 1.2f)
            setPadding(0, if (title.isNullOrBlank()) 0 else dp(10, density), 0, 0)
        })

        if (buttons.isNotEmpty()) {
            val buttonRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(18, density), 0, 0)
            }
            buttons.forEach { button ->
                buttonRow.addView(buildButton(activity, density, button))
            }
            card.addView(buttonRow)
        }

        return card
    }

    /**
     * ONE reusable button style for every AkAI dialog: the brand-blue 3D pill with
     * white Poppins-bold text, and a consistent height / minimum width / padding so
     * longer labels never change the button's footprint.
     */
    private fun buildButton(activity: Activity, density: Float, button: Button): TextView {
        // Text-only "Skip" control — mirrors the tutorial's Skip exactly: no button
        // background, no 3D surface, no shadow/depth. Height matches the pill so the
        // other button in the row never shifts.
        if (button.textOnly) {
            return TextView(activity).apply {
                text = button.label
                textSize = 13f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(Color.parseColor("#8FA6BE"))
                typeface = poppins(activity, bold = true)
                setPadding(dp(6, density), 0, dp(6, density), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dismissNow()
                    button.onClick()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(48, density)
                ).apply {
                    leftMargin = dp(6, density)
                    rightMargin = dp(6, density)
                }
            }
        }

        return TextView(activity).apply {
            text = button.label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = poppins(activity, bold = true)
            background = activity.getDrawable(R.drawable.bg_btn_blue_3d)
            setPadding(
                dp(22, density), 0,
                dp(22, density), dp(3, density)
            )
            minimumWidth = dp(120, density)
            minimumHeight = dp(48, density)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dismissNow()
                button.onClick()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(48, density)
            ).apply {
                leftMargin = dp(6, density)
                rightMargin = dp(6, density)
            }
        }
    }

    private fun themedCardBackground(surface: Int, depth: Int, density: Float): LayerDrawable {
        val radius = (16f * density)
        val depthLayer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(depth)
            cornerRadius = radius
        }
        val surfaceLayer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(surface)
            cornerRadius = radius
        }
        val inset = (2.5f * density).toInt()
        val layers = LayerDrawable(arrayOf(depthLayer, surfaceLayer))
        layers.setLayerInset(0, 0, inset, 0, 0)
        layers.setLayerInset(1, 0, 0, 0, inset)
        return layers
    }

    private fun poppins(context: Context, bold: Boolean): Typeface {
        val font = if (bold) context.resources.getFont(R.font.poppins_bold) else context.resources.getFont(R.font.poppins_regular)
        return Typeface.create(font, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun isDarkMode(activity: Activity): Boolean {
        val mode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()

    private fun dismissNow() {
        val host = activeHost
        val root = activeRoot
        if (host != null && root != null && root.parent === host) {
            host.removeView(root)
        }
        activeHost = null
        activeRoot = null
    }
}