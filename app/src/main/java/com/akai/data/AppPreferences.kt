package com.akai.data

import android.graphics.Color

object AppPreferences {
    const val PREFS_NAME             = "akai_settings"
    const val KEY_DEAF_BUBBLE_COLOR  = "deaf_bubble_color"
    const val KEY_HEARING_BUBBLE_COLOR = "hearing_bubble_color"
    const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    const val KEY_TTS_VOICE_PERSONA  = "tts_voice_persona"
    val DEFAULT_DEAF_BUBBLE_COLOR    = Color.parseColor("#A7C7E7")
    val DEFAULT_HEARING_BUBBLE_COLOR = Color.parseColor("#B8E6C1")
}
