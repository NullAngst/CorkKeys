/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

/**
 * The two symbol pages behind ?123. Page one: digits, then the letter keys' long-press symbols
 * in the same order, plus / and the rest of the common punctuation. Page two: currency, math,
 * marks and the less common brackets. The currency key follows the language.
 */
object LayoutSymbols {
    /** Long-press variants on the symbol pages. The key itself comes first. */
    private val variants: Map<String, List<String>> = mapOf(
        "$" to listOf("$", "€", "£", "¥", "¢", "₹"),
        "€" to listOf("€", "$", "£", "¥", "¢", "₹"),
        "-" to listOf("-", "–", "—", "_", "·"),
        "\"" to listOf("\"", "“", "”", "„", "«", "»"),
        "'" to listOf("'", "‘", "’", "‚", "‹", "›"),
        "?" to listOf("?", "¿", "‽"),
        "!" to listOf("!", "¡"),
        "%" to listOf("%", "‰"),
        "*" to listOf("*", "†", "‡", "★"),
        "=" to listOf("=", "≠", "≈"),
        "+" to listOf("+", "±"),
        "<" to listOf("<", "≤", "«", "‹"),
        ">" to listOf(">", "≥", "»", "›"),
        "(" to listOf("(", "[", "{"),
        ")" to listOf(")", "]", "}"),
        "#" to listOf("#", "№"),
        "/" to listOf("/", "÷"),
        "." to listOf(".", "…"),
        "1" to listOf("1", "¹", "½", "⅓", "¼"),
        "2" to listOf("2", "²", "⅔"),
        "3" to listOf("3", "³", "¾"),
        "0" to listOf("0", "°", "∅"),
    )

    private fun sym(s: String) = Key(s, popup = variants[s].orEmpty())

    private fun bottom(units: Float, emojiComma: Boolean) = listOf(
        Key("ABC", kind = Kind.LETTERS, width = 1.5f),
        Key.comma(emojiComma),
        Key("", " ", Kind.SPACE, width = units - 5f),
        Key(".", popup = variants.getValue(".")),
        Key("", "\n", Kind.ENTER, width = 1.5f),
    )

    fun page1(language: String, emojiComma: Boolean = true): Layout {
        val cur = LongPressSymbols.currency(language)
        val rows = listOf(
            "1234567890".map { sym(it.toString()) },
            LongPressSymbols.rows[0].map { sym(it) },
            LongPressSymbols.rows[1].map { sym(if (it == "$") cur else it) } + sym("/"),
            listOf(Key("=\\<", kind = Kind.SYMBOLS_MORE, width = 1.5f)) +
                LongPressSymbols.rows[2].map { sym(it) } +
                Key("", kind = Kind.BACKSPACE, width = 1.5f),
            bottom(10f, emojiComma),
        )
        return Layout("symbols_$language", rows)
    }

    fun page2(language: String, emojiComma: Boolean = true): Layout {
        val first = if (LongPressSymbols.currency(language) == "€") "$" else "€"
        val currencies = listOf(first, "£", "¥", "¢", "₹", "₩", "₽")
        val rows = listOf(
            listOf("~", "`", "^", "°", "•", "·", "¶", "§", "©", "®").map { sym(it) },
            listOf("×", "÷", "±", "≠", "≈", "≤", "≥", "√", "∞", "π").map { sym(it) },
            (currencies + listOf("™", "‰", "…")).map { sym(it) },
            listOf(Key("?123", kind = Kind.SYMBOLS, width = 1.5f)) +
                listOf("«", "»", "‹", "›", "⟨", "⟩", "¬").map { sym(it) } +
                Key("", kind = Kind.BACKSPACE, width = 1.5f),
            bottom(10f, emojiComma),
        )
        return Layout("symbols2_$language", rows)
    }
}
