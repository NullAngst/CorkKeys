/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

/**
 * The number pad for number fields, and for date and time fields with / and : in place of
 * - and , . Opens on its own from the field type. ?123 still reaches the full symbol page.
 */
object LayoutNumber {
    private const val W = 2.5f

    private fun k(s: String, vararg more: String) = Key(s, width = W, popup = if (more.isEmpty()) emptyList() else listOf(s) + more)

    fun build(dateTime: Boolean): Layout {
        val a = if (dateTime) k("/", "-", ".") else k("-", "+", "±")
        val b = if (dateTime) k(":", ",", ".") else k(",", ".", ":", "/")
        val rows = listOf(
            listOf(k("1"), k("2"), k("3"), a),
            listOf(k("4"), k("5"), k("6"), b),
            listOf(k("7"), k("8"), k("9"), Key("", kind = Kind.BACKSPACE, width = W)),
            listOf(Key("?123", kind = Kind.SYMBOLS, width = W), k("0"), k(".", ","), Key("", "\n", Kind.ENTER, width = W)),
        )
        return Layout(if (dateTime) "number_datetime" else "number", rows)
    }
}
