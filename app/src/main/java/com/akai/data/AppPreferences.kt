package com.akai.data

import android.graphics.Color

object AppPreferences {
    const val PREFS_NAME             = "akai_settings"
    const val KEY_DEAF_BUBBLE_COLOR  = "deaf_bubble_color"
    const val KEY_HEARING_BUBBLE_COLOR = "hearing_bubble_color"
    const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    const val KEY_PERMISSION_SETUP_DONE = "permission_setup_done"
    const val KEY_TTS_VOICE_PERSONA  = "tts_voice_persona"
    const val KEY_THEME_MODE         = "theme_mode"
    const val KEY_SETTINGS_PAGE      = "settings_page"
    const val PAGE_MAIN              = "main"
    const val PAGE_AUDIO             = "audio"
    const val PAGE_PERSONALIZATION   = "personalization"
    const val PAGE_CUSTOM_COLOR      = "custom_color"
    val DEFAULT_DEAF_BUBBLE_COLOR    = Color.parseColor("#5796DB")
    val DEFAULT_HEARING_BUBBLE_COLOR = Color.parseColor("#FFFFFF")
}
