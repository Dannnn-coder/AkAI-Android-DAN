package com.akai

import android.app.Activity
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Keeps the Android system bars (status + navigation) in sync with the active
 * AkAI theme. Light Mode -> dark icons on a light bar; Dark Mode -> light icons
 * on a dark bar.
 *
 * Uses the modern AndroidX [WindowInsetsControllerCompat] so it works from the
 * app's min SDK (26) through the latest edge-to-edge devices. Explicit bar
 * colors are also set as a fallback for API 26-34; on API 35+ the bars are
 * edge-to-edge transparent and the app draws its own themed background.
 */
object SystemBarTheme {

    fun apply(activity: Activity) {
        val darkMode = isDarkMode(activity)
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        // Icons/buttons: dark on light surfaces, light on dark surfaces.
        val lightAppearance = !darkMode
        controller.isAppearanceLightStatusBars = lightAppearance
        controller.isAppearanceLightNavigationBars = lightAppearance

        // Fallback bar colors for API 26-34 (ignored on API 35+ edge-to-edge).
        val barColor = ContextCompat.getColor(activity, R.color.theme_system_bar_bg)
        window.statusBarColor = barColor
        window.navigationBarColor = barColor
    }

    private fun isDarkMode(activity: Activity): Boolean {
        val mode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }
}