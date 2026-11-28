/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

/** English: QWERTY, with the accents people reach for in borrowed words on long-press. */
object LayoutEnglish {
    val data = LetterData(
        language = "en",
        rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        accents = mapOf(
            'a' to "àáâäæãåā",
            'c' to "çćč",
            'e' to "èéêëē",
            'i' to "îïíīì",
            'n' to "ñń",
            'o' to "ôöòóœøōõ",
            's' to "ßśš",
            'u' to "ûüùúū",
            'y' to "ÿ",
            'z' to "žźż",
        ),
    )
}
