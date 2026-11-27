/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

/** The optional row of digits above the letters. Off by default. */
object NumberRow {
    private val variants = mapOf(
        '1' to "¹½⅓¼⅛",
        '2' to "²⅔",
        '3' to "³¾⅜",
        '4' to "⁴",
        '5' to "⅝",
        '7' to "⅞",
        '0' to "°∅ⁿ",
    )

    fun keys(): List<Key> = "1234567890".map { d ->
        val more = variants[d].orEmpty().map { it.toString() }
        Key(d.toString(), popup = if (more.isEmpty()) emptyList() else listOf(d.toString()) + more)
    }
}
