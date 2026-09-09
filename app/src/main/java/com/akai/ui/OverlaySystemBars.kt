package com.akai.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.akai.R
import com.akai.SystemBarTheme

/** Walks ContextWrapper chains to the nearest Activity (overlays are built with an Activity context). */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Full-screen blue overlay (Dual Device / Host / Join / Connected) bar handling:
 *  - STATUS bar stays akai-blue so it merges with the blue page (no ugly strip at
 *    the top) on API 26-34, where the color is honored.
 *  - NAVIGATION bar follows the THEME (white in Light, dark in Dark) so it is never
 *    left blue. Its icon appearance stays theme-driven at the window level.
 *  - On API 35+ these color setters are ignored (edge-to-edge); the icon appearance
 *    set by [SystemBarTheme] still applies, which is what matters for visibility.
 *
 * Call from [View.onAttachedToWindow] / [View.onDetachedFromWindow].
 */
fun View.paintSystemBarsForOverlay() {
    val activity = context.findActivity() ?: return
    activity.window.statusBarColor = ContextCompat.getColor(context, R.color.akai_blue)
    activity.window.navigationBarColor = ContextCompat.getColor(context, R.color.theme_system_bar_bg)
    // The blue page needs WHITE status icons even when the app is in Light Mode
    // (otherwise dark icons would be invisible on the blue status bar). The
    // navigation bar stays theme-driven: dark icons on the light nav bar, and
    // vice-versa, set at the window level by SystemBarTheme.
    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    controller.isAppearanceLightStatusBars = false
}

fun View.restoreSystemBarsForOverlay() {
    context.findActivity()?.let { SystemBarTheme.apply(it) }
}