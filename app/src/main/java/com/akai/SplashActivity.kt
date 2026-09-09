package com.akai

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.akai.data.AppPreferences
import com.akai.viewmodel.ConversationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    // One-shot branded sequence (ms) — no loading bar, no artificial wait.
    private val FADE_IN_MS     = 360L // logo + "AkAI" appear together
    private val PAUSE_MS       = 150L // brief hold after the fade-in
    private val SHIFT_TAGLINE_MS = 420L // group eases up + tagline fades in
    private val ANIMATION_TOTAL_MS = FADE_IN_MS + PAUSE_MS + SHIFT_TAGLINE_MS

    private var startTime   = 0L
    private var modelsReady = false

    private lateinit var viewModel : ConversationViewModel
    private lateinit var logoImage : ImageView
    private lateinit var appName   : TextView
    private lateinit var tagline   : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the saved theme preference (default Dark Mode) before the
        // splash renders, so a fresh install launches in Dark Mode.
        prefs().let {
            AppCompatDelegate.setDefaultNightMode(
                it.getInt(AppPreferences.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_YES)
            )
        }
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Sync the system status/navigation bars with the applied theme.
        SystemBarTheme.apply(this)

        startTime = System.currentTimeMillis()
        logoImage = findViewById(R.id.splashLogo)
        appName   = findViewById(R.id.splashAppName)
        tagline   = findViewById(R.id.splashTagline)

        viewModel = ViewModelProvider(this)[ConversationViewModel::class.java]

        initViewStates()
        startEntranceAnimation()
        observeModelReady()
    }

    // ────────────────────────────────────────────
    // INITIAL STATES
    // ────────────────────────────────────────────
    // Everything fades in from pure white (alpha 0). No position offset up
    // front — the group appears centered, THEN rises in phase 2.

    private fun initViewStates() {
        logoImage.alpha = 0f
        appName.alpha   = 0f
        tagline.alpha   = 0f
        logoImage.translationY = 0f
        appName.translationY   = 0f
    }

    // ────────────────────────────────────────────
    // ENTRANCE ANIMATION
    // ────────────────────────────────────────────
    // Sequence: white → logo+"AkAI" fade in together → brief pause → the group
    // rises slightly up (smooth EASE-OUT) while the tagline fades in below it.
    // No bounce, no hover, no looping, no loading indicator.

    private fun startEntranceAnimation() {
        val easeOut = DecelerateInterpolator(2f)
        val shiftUp = 26f.dpToPx()

        // Phase 1 — logo + app name fade in together.
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logoImage, "alpha", 0f, 1f).apply {
                    duration     = FADE_IN_MS
                    interpolator = easeOut
                },
                ObjectAnimator.ofFloat(appName, "alpha", 0f, 1f).apply {
                    duration     = FADE_IN_MS
                    interpolator = easeOut
                }
            )
            start()
        }

        // Phase 2 — after a brief natural pause, the whole group shifts up
        // (ease-out) while the tagline fades in underneath. The tagline stays
        // in place — it only fades, it never slides on its own.
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logoImage, "translationY", 0f, -shiftUp).apply {
                    duration     = SHIFT_TAGLINE_MS
                    interpolator = easeOut
                },
                ObjectAnimator.ofFloat(appName, "translationY", 0f, -shiftUp).apply {
                    duration     = SHIFT_TAGLINE_MS
                    interpolator = easeOut
                },
                ObjectAnimator.ofFloat(tagline, "alpha", 0f, 1f).apply {
                    duration     = 360L
                    startDelay   = 100
                    interpolator = DecelerateInterpolator(1.2f)
                }
            )
            startDelay = FADE_IN_MS + PAUSE_MS
            start()
        }
    }

    // ────────────────────────────────────────────
    // MODEL READY
    // ────────────────────────────────────────────
    // Transition once the animation has had time to finish AND the splash's
    // background model load has resolved. The app never lingers just to play
    // a longer animation — if the model is already ready early, we only wait
    // out the rest of the animation (instantly if it already finished).

    private fun observeModelReady() {
        viewModel.modelsReady.observe(this) { ready ->
            if (ready && !modelsReady) {
                modelsReady = true
                val elapsed  = System.currentTimeMillis() - startTime
                val remaining = maxOf(0L, ANIMATION_TOTAL_MS - elapsed)
                // lifecycleScope auto-cancels — transitionToMain() never fires on
                // a destroyed activity even if modelsReady arrives at the last moment
                lifecycleScope.launch {
                    delay(remaining)
                    transitionToMain()
                }
            }
        }
    }

    // ────────────────────────────────────────────
    // TRANSITION
    // ────────────────────────────────────────────

    private fun transitionToMain() {
        if (isFinishing || isDestroyed) return  // extra safety guard
        // First launch: route through the blue permission setup screen before
        // entering the main application (only once — tracked via preferences).
        val needsPermissionSetup = !prefs().getBoolean(AppPreferences.KEY_PERMISSION_SETUP_DONE, false)
        val destination = if (needsPermissionSetup) PermissionActivity::class.java else MainActivity::class.java
        startActivity(Intent(this, destination))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    // ────────────────────────────────────────────
    // EXTENSION
    // ────────────────────────────────────────────

    private fun Float.dpToPx(): Float =
        this * resources.displayMetrics.density

    private fun prefs(): android.content.SharedPreferences =
        getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
}