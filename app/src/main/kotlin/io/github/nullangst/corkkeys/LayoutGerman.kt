/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

/** German: QWERTZ with ü, ö and ä on the main layer. ß and ẞ sit on s. */
object LayoutGerman {
    val data = LetterData(
        language = "de",
        rows = listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm"),
        accents = mapOf(
            'a' to "äàáâæãåā",
            'c' to "çćč",
            'e' to "éèêëėē",
            'i' to "íìîïī",
            'n' to "ñń",
            'o' to "öóòôõœøō",
            's' to "ßẞśš",
            'u' to "üúùûū",
            'y' to "ÿ",
            'z' to "žźż",
        ),
        periodExtra = listOf("„", "“"),
    )
}
