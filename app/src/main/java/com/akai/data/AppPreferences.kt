package com.akai.data

import android.graphics.Color

object AppPreferences {
    const val PREFS_NAME             = "akai_settings"
    const val KEY_DEAF_BUBBLE_COLOR  = "deaf_bubble_color"
    const val KEY_HEARING_BUBBLE_COLOR = "hearing_bubble_color"
    const val KEY_TTS_GENDER         = "tts_gender"
    const val TTS_FEMALE             = "female"
    const val TTS_MALE               = "male"
    val DEFAULT_DEAF_BUBBLE_COLOR    = Color.parseColor("#A7C7E7")
    val DEFAULT_HEARING_BUBBLE_COLOR = Color.parseColor("#B8E6C1")
}
