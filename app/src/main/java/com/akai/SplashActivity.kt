package com.akai

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.akai.viewmodel.ConversationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private val MIN_DISPLAY_MS         = 2000L
    private val SLOW_LOAD_THRESHOLD_MS = 2500L

    private var startTime   = 0L
    private var modelsReady = false

    private lateinit var viewModel     : ConversationViewModel
    private lateinit var logoImage     : ImageView
    private lateinit var glowView      : View
    private lateinit var tagline       : TextView
    private lateinit var accentLine    : View
    private lateinit var versionText   : TextView
    private lateinit var progressLayout: LinearLayout

    private var pulseAnimSet: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        startTime      = System.currentTimeMillis()
        glowView       = findViewById(R.id.splashGlow)
        logoImage      = findViewById(R.id.splashLogo)
        tagline        = findViewById(R.id.splashTagline)
        accentLine     = findViewById(R.id.splashAccentLine)
        versionText    = findViewById(R.id.splashVersion)
        progressLayout = findViewById(R.id.progressLayout)

        viewModel = ViewModelProvider(this)[ConversationViewModel::class.java]

        initViewStates()
        startEntranceAnimation()
        scheduleSlowLoadIndicator()
        observeModelReady()
    }

    // ────────────────────────────────────────────
    // INITIAL STATES
    // ────────────────────────────────────────────

    private fun initViewStates() {
        glowView.alpha       = 0f
        logoImage.alpha      = 0f
        logoImage.scaleX     = 0.75f
        logoImage.scaleY     = 0.75f
        tagline.alpha        = 0f
        tagline.translationY = 40f.dpToPx()
        accentLine.alpha     = 0f
        accentLine.scaleX    = 0f
        // NOTE: pivotX is NOT set here — the system default is already center (width/2).
        // Setting it at onCreate time would set it to 0 because the view hasn't been
        // measured yet, which would cause the line to expand from the left edge instead.
        versionText.alpha    = 0f
    }

    // ────────────────────────────────────────────
    // ANIMATIONS
    // ────────────────────────────────────────────

    private fun startEntranceAnimation() {
        val decel    = DecelerateInterpolator()
        val smoothIO = AccelerateDecelerateInterpolator()

        // Phase 1 (0ms) — Glow fades in softly behind logo
        ObjectAnimator.ofFloat(glowView, "alpha", 0f, 1f).apply {
            duration     = 1000
            interpolator = decel
            start()
        }

        // Phase 1 (0ms) — Logo: fade in + scale up with slight overshoot
        val logoFade = ObjectAnimator.ofFloat(logoImage, "alpha", 0f, 1f).apply {
            duration     = 900
            interpolator = decel
        }
        val logoSX = ObjectAnimator.ofFloat(logoImage, "scaleX", 0.75f, 1f).apply {
            duration     = 900
            interpolator = OvershootInterpolator(1.2f)
        }
        val logoSY = ObjectAnimator.ofFloat(logoImage, "scaleY", 0.75f, 1f).apply {
            duration     = 900
            interpolator = OvershootInterpolator(1.2f)
        }
        AnimatorSet().apply { playTogether(logoFade, logoSX, logoSY); start() }

        // Phase 2 (650ms) — Tagline slides up + fades in
        ObjectAnimator.ofFloat(tagline, "translationY", 40f.dpToPx(), 0f).apply {
            duration     = 700
            startDelay   = 650
            interpolator = decel
            start()
        }
        ObjectAnimator.ofFloat(tagline, "alpha", 0f, 1f).apply {
            duration     = 700
            startDelay   = 650
            interpolator = decel
            start()
        }

        // Phase 3 (950ms) — Accent line expands from center
        ObjectAnimator.ofFloat(accentLine, "scaleX", 0f, 1f).apply {
            duration     = 600
            startDelay   = 950
            interpolator = smoothIO
            start()
        }
        ObjectAnimator.ofFloat(accentLine, "alpha", 0f, 0.85f).apply {
            duration   = 500
            startDelay = 950
            start()
        }

        // Phase 4 (1100ms) — Version text fades in quietly
        ObjectAnimator.ofFloat(versionText, "alpha", 0f, 1f).apply {
            duration   = 600
            startDelay = 1100
            start()
        }

        // Phase 5 (1000ms) — Start pulse — uses lifecycleScope so it auto-cancels
        // if the activity is destroyed before the delay fires (e.g. user presses back)
        lifecycleScope.launch {
            delay(1000)
            startPulse()
        }
    }

    private fun startPulse() {
        val lX = ObjectAnimator.ofFloat(logoImage, "scaleX", 1f, 1.05f, 1f).apply {
            duration     = 1800
            repeatCount  = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val lY = ObjectAnimator.ofFloat(logoImage, "scaleY", 1f, 1.05f, 1f).apply {
            duration     = 1800
            repeatCount  = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val gX = ObjectAnimator.ofFloat(glowView, "scaleX", 1f, 1.12f, 1f).apply {
            duration     = 1800
            repeatCount  = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val gY = ObjectAnimator.ofFloat(glowView, "scaleY", 1f, 1.12f, 1f).apply {
            duration     = 1800
            repeatCount  = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        pulseAnimSet = AnimatorSet().apply {
            playTogether(lX, lY, gX, gY)
            start()
        }
    }

    // ────────────────────────────────────────────
    // SLOW-LOAD SPINNER
    // ────────────────────────────────────────────

    private fun scheduleSlowLoadIndicator() {
        // lifecycleScope auto-cancels when activity is destroyed — no crash risk
        lifecycleScope.launch {
            delay(SLOW_LOAD_THRESHOLD_MS)
            if (!modelsReady) {
                progressLayout.animate().alpha(1f).setDuration(400).start()
            }
        }
    }

    // ────────────────────────────────────────────
    // MODEL READY
    // ────────────────────────────────────────────

    private fun observeModelReady() {
        viewModel.modelsReady.observe(this) { ready ->
            if (ready) {
                modelsReady = true
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = maxOf(0L, MIN_DISPLAY_MS - elapsed)
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
        pulseAnimSet?.cancel()
        startActivity(Intent(this, MainActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        pulseAnimSet?.cancel()
        // lifecycleScope coroutines are automatically cancelled here — no leaks
    }

    // ────────────────────────────────────────────
    // EXTENSION
    // ────────────────────────────────────────────

    private fun Float.dpToPx(): Float =
        this * resources.displayMetrics.density
}
