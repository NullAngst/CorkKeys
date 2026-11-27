/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

/**
 * The symbol on each letter key's long-press, by position, the same map Gboard uses. Every
 * language gets the same symbol in the same spot, whatever letter sits there: on QWERTZ the
 * key where Y would be still has ']'. Keys past the end of a row (ü, ö, ä, ñ) have none.
 */
object LongPressSymbols {
    val rows: List<List<String>> = listOf(
        listOf("%", "\\", "|", "=", "[", "]", "<", ">", "{", "}"),
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")"),
        listOf("*", "\"", "'", ":", ";", "!", "?"),
    )

    /** The currency sign a language reaches for first. */
    fun currency(language: String): String = if (language == "de" || language == "es") "€" else "$"

    fun at(row: Int, col: Int, language: String): String? {
        val s = rows.getOrNull(row)?.getOrNull(col) ?: return null
        return if (s == "$") currency(language) else s
    }
}
