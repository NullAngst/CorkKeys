/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

/** The phone pad for phone number fields, letters shown on the digits as on a dial pad. */
object LayoutPhone {
    private const val W = 2.5f

    private fun d(s: String, letters: String? = null) = Key(s, width = W, corner = letters)

    fun build(): Layout {
        val rows = listOf(
            listOf(d("1"), d("2", "ABC"), d("3", "DEF"), Key("-", width = W, popup = listOf("-", "(", ")", "/"))),
            listOf(d("4", "GHI"), d("5", "JKL"), d("6", "MNO"), Key("+", width = W, popup = listOf("+", ",", ";", "N", "."))),
            listOf(d("7", "PQRS"), d("8", "TUV"), d("9", "WXYZ"), Key("", kind = Kind.BACKSPACE, width = W)),
            listOf(d("*"), Key("0", width = W, corner = "+", popup = listOf("+")), d("#"), Key("", "\n", Kind.ENTER, width = W)),
        )
        return Layout("phone", rows)
    }
}
