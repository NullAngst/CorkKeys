/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

/**
 * Spanish: QWERTY with ñ on the main layer, accents on every vowel, and ¡ and ¿ right after
 * ! and ? on the n and m long-press.
 */
object LayoutSpanish {
    val data = LetterData(
        language = "es",
        rows = listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm"),
        accents = mapOf(
            'a' to "áàäâãåāª",
            'c' to "çćč",
            'e' to "éèëêē",
            'i' to "íìïî",
            'm' to "¿",
            'n' to "¡ń",
            'o' to "óòöôõøōº",
            'u' to "úüùû",
            'y' to "ÿ",
        ),
        periodExtra = listOf("¿", "¡"),
    )
}
