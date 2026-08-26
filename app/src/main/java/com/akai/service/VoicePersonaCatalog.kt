package com.akai.service

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice

/**
 * A named "persona" pairing an English voice with a Filipino voice (if the device has one),
 * so the same persona can read either language in the same character.
 */
data class VoicePersona(
    val name: String,
    val englishVoice: Voice,
    val filipinoVoice: Voice?
)

/**
 * Lists the device's installed local TTS voices as named personas. Android's TTS API doesn't
 * expose voice gender, and most engines' voice names are opaque codes (e.g. "en-us-x-tpf-local")
 * with nothing to reliably tell male from female — so these are presented ungrouped, to be
 * chosen by ear rather than mislabeled.
 */
object VoicePersonaCatalog {
    // Jordan/Quinn intentionally swapped from alphabetical-voice order — verified by ear
    // that the voice landing on index 2 sounded more like "Quinn" and vice versa.
    private val PERSONA_NAMES = listOf("Alex", "Sam", "Quinn", "Casey", "Riley", "Morgan", "Taylor", "Jordan")

    fun build(tts: TextToSpeech): List<VoicePersona> {
        val allVoices = tts.voices ?: emptySet()
        val englishVoices = allVoices
            .filter { !it.isNetworkConnectionRequired && it.locale.language == "en" && isRealVoice(it) }
            .sortedBy { it.name }
        val filipinoVoices = allVoices
            .filter { !it.isNetworkConnectionRequired && it.locale.language == "fil" && isRealVoice(it) }
            .sortedBy { it.name }

        val personas = englishVoices.take(PERSONA_NAMES.size).mapIndexed { index, voice ->
            // Cycle through the (usually far fewer) Filipino voices so every persona gets a
            // real Tagalog voice instead of only the first few — sharing is better than none.
            val filipinoVoice = filipinoVoices.takeIf { it.isNotEmpty() }?.get(index % filipinoVoices.size)
            VoicePersona(PERSONA_NAMES[index], voice, filipinoVoice)
        }

        // Sam/Quinn and Morgan/Taylor's Tagalog voices swapped — verified by ear that each
        // pair's cycled-in Tagalog voice actually matched the other's English voice better.
        return swapFilipinoVoices(swapFilipinoVoices(personas, "Sam", "Quinn"), "Morgan", "Taylor")
    }

    private fun swapFilipinoVoices(personas: List<VoicePersona>, nameA: String, nameB: String): List<VoicePersona> {
        val a = personas.find { it.name == nameA } ?: return personas
        val b = personas.find { it.name == nameB } ?: return personas
        return personas.map {
            when (it.name) {
                nameA -> it.copy(filipinoVoice = b.filipinoVoice)
                nameB -> it.copy(filipinoVoice = a.filipinoVoice)
                else -> it
            }
        }
    }

    /** Excludes generic "any voice for this language" placeholder entries, not real distinct voices. */
    private fun isRealVoice(voice: Voice): Boolean =
        !voice.features.contains("legacySetLanguageVoice")
}
