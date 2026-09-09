package com.akai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.akai.data.AppPreferences

/**
 * First-time permission screen: a friendly, all-blue setup gate shown once before
 * the main application. Each required permission has a switch that reflects the
 * REAL Android permission state; "Done!" stays disabled until CAMERA and
 * RECORD_AUDIO are both actually granted.
 */
class PermissionActivity : AppCompatActivity() {

    private lateinit var cameraSwitch: SwitchCompat
    private lateinit var micSwitch: SwitchCompat
    private lateinit var doneButton: TextView

    companion object {
        private const val REQUEST_CAMERA = 200
        private const val REQUEST_MIC    = 201
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeFromPreference()
        SystemBarTheme.apply(this)
        // This screen is permanently blue regardless of theme.
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.parseColor("#5796DB")
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.parseColor("#5796DB")
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        buildUi()
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.parseColor("#5796DB"))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (22f * density).toInt(),
                (24f * density).toInt(),
                (22f * density).toInt(),
                (24f * density).toInt()
            )
        }

        // Exact system-inset handling (edge-to-edge on modern Android).
        ViewCompat.setOnApplyWindowInsetsListener(column) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                (22f * density).toInt(),
                bars.top + (24f * density).toInt(),
                (22f * density).toInt(),
                bars.bottom + (24f * density).toInt()
            )
            insets
        }

        column.addView(TextView(this).apply {
            text = "Permissions"
            textSize = 30f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = font(bold = true)
        })
        column.addView(TextView(this).apply {
            text = "AkAI needs access to your camera and microphone so you can use sign recognition and voice features."
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#E8F0FF"))
            typeface = font(bold = false)
            setLineSpacing(0f, 1.2f)
            setPadding(0, (14f * density).toInt(), 0, (26f * density).toInt())
        })

        cameraSwitch = permissionSwitch(
            title = "Camera",
            description = "Allow AkAI to use your camera for Filipino Sign Language recognition.",
            granted = permissionGranted(Manifest.permission.CAMERA)
        )
        column.addView(cameraSwitch.tag as ViewGroup)

        micSwitch = permissionSwitch(
            title = "Microphone",
            description = "Allow AkAI to use your microphone for speech recognition.",
            granted = permissionGranted(Manifest.permission.RECORD_AUDIO)
        )
        column.addView(micSwitch.tag as ViewGroup)

        doneButton = TextView(this).apply {
            text = "Done!"
            textSize = 17f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#5796DB"))
            typeface = font(bold = true)
            background = getDrawable(R.drawable.bg_btn_white_3d)
            setPadding(
                (30f * density).toInt(), (16f * density).toInt(),
                (30f * density).toInt(), (18f * density).toInt()
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onDone() }
        }
        column.addView(doneButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        scroll.addView(column)
        setContentView(scroll)
        updateDoneState()
    }

    /** Builds a rounded, translucent permission card. The Switch is stashed in [View.tag]. */
    private fun permissionSwitch(title: String, description: String, granted: Boolean): SwitchCompat {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = (14f * density)
            }
            setPadding(
                (16f * density).toInt(), (14f * density).toInt(),
                (14f * density).toInt(), (14f * density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12f * density).toInt() }
        }

        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@PermissionActivity).apply {
                text = title
                textSize = 17f
                setTextColor(Color.WHITE)
                typeface = font(bold = true)
            })
            addView(TextView(this@PermissionActivity).apply {
                text = description
                textSize = 13f
                setTextColor(Color.parseColor("#E8F0FF"))
                typeface = font(bold = false)
                setLineSpacing(0f, 1.15f)
                setPadding(0, (4f * density).toInt(), 0, 0)
            })
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val sw = SwitchCompat(this).apply {
            setChecked(granted)
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf()), intArrayOf(Color.WHITE)
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, Color.parseColor("#59FFFFFF"))
            )
            showText = false

            // Reflect real state; a granted switch can't be turned OFF (Android cannot
            // programmatically revoke a runtime permission), so snap granted switches
            // back ON instead of pretending. OFF -> ON requests the real permission.
            setOnCheckedChangeListener { _, checked ->
                val name = if (this === cameraSwitch) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO
                val requestCode = if (this === cameraSwitch) REQUEST_CAMERA else REQUEST_MIC
                if (checked && !permissionGranted(name)) {
                    ActivityCompat.requestPermissions(
                        this@PermissionActivity, arrayOf(name), requestCode
                    )
                } else if (!checked && permissionGranted(name)) {
                    // Cannot revoke here — show the truth.
                    setChecked(true)
                }
                updateDoneState()
            }
            tag = row
        }
        row.addView(sw)

        // Grant the row description/switch pairing — the row itself is exposed via tag.
        row.contentDescription = "$title — ${if (granted) "granted" else "off"}"
        return sw
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Reflect the REAL result — both granted and denied are mirrored faithfully.
        cameraSwitch.setChecked(permissionGranted(Manifest.permission.CAMERA))
        micSwitch.setChecked(permissionGranted(Manifest.permission.RECORD_AUDIO))
        updateDoneState()
    }

    private fun permissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** Done stays disabled until BOTH required permissions are really granted. */
    private fun updateDoneState() {
        val both = permissionGranted(Manifest.permission.CAMERA) &&
            permissionGranted(Manifest.permission.RECORD_AUDIO)
        doneButton.isEnabled = both
        doneButton.alpha = if (both) 1f else 0.45f
        doneButton.isClickable = both
    }

    private fun onDone() {
        prefs().edit().putBoolean(AppPreferences.KEY_PERMISSION_SETUP_DONE, true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun applyThemeFromPreference() {
        val saved = prefs().getInt(AppPreferences.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_YES)
        AppCompatDelegate.setDefaultNightMode(saved)
    }

    private fun font(bold: Boolean): Typeface {
        val styled = if (bold) resources.getFont(R.font.poppins_bold) else resources.getFont(R.font.poppins_regular)
        return Typeface.create(styled, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun prefs(): android.content.SharedPreferences =
        getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
}