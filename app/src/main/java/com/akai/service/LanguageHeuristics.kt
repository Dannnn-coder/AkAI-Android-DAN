package com.akai.service

import java.util.Locale

private val TAGALOG_MARKERS = setOf(
    "ang", "ng", "mga", "sa", "ako", "ikaw", "siya", "kami", "tayo", "kayo", "sila",
    "ito", "iyan", "iyon", "hindi", "oo", "opo", "po", "kumusta", "salamat",
    "at", "na", "ay", "mo", "ko", "niya", "nila", "namin", "natin", "ninyo",
    "ba", "din", "rin", "lang", "para", "dahil", "kasi", "pero", "kung", "gusto",
    "maganda", "mabuti", "paano", "saan", "kailan", "bakit", "sino", "alin",
    "wala", "meron", "mayroon"
)

/** Best-effort heuristic: true if [text] contains common Tagalog function words. */
fun looksLikeTagalog(text: String): Boolean {
    val words = text.lowercase(Locale.US).split(Regex("[^a-zà-ÿ']+")).filter { it.isNotBlank() }
    return words.any { it in TAGALOG_MARKERS }
}
