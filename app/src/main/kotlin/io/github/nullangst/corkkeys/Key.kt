/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

/** What pressing a key does. Everything that is not CHAR is handled by the keyboard itself. */
enum class Kind {
    CHAR, SHIFT, BACKSPACE, SPACE, ENTER,
    SYMBOLS, SYMBOLS_MORE, LETTERS,
    ONE_HANDED_FLIP, ONE_HANDED_EXIT,
}

/**
 * One key: what it shows, what it types, the small symbol in its corner, the long-press list and
 * how wide it is, in key units. The first entry of [popup] is what a long-press types when the
 * finger lifts without sliding.
 */
class Key(
    val label: String,
    val output: String = label,
    val kind: Kind = Kind.CHAR,
    val corner: String? = null,
    val popup: List<String> = emptyList(),
    val width: Float = 1f,
    /** Opens settings on long-press instead of a popup. The comma key. */
    val longPressSettings: Boolean = false,
) {
    val isLetter: Boolean get() = kind == Kind.CHAR && output.length == 1 && output[0].isLetter()

    /** What the key shows and types with shift on. */
    fun shifted(): String = shiftText(output)

    companion object {
        /** Upper case for one key's text. ß becomes ẞ instead of "SS", so it stays one letter. */
        fun shiftText(s: String): String = when (s) {
            "ß" -> "ẞ"
            else -> if (s.length == 1 && s[0].isLetter()) s.uppercase() else s
        }

        /** A long-press list with shift applied, duplicates dropped (ß and ẞ both become ẞ). */
        fun shiftPopup(list: List<String>): List<String> = list.map { shiftText(it) }.distinct()
    }
}

/** A full set of key rows, top to bottom. */
class Layout(val id: String, val rows: List<List<Key>>) {
    /** Widest row in key units. Rows narrower than this are centred. */
    val units: Float get() = maxOf(10f, rows.maxOf { r -> r.sumOf { it.width.toDouble() }.toFloat() })
}

/**
 * The plain data one language's letter layout is made of: three rows of letters and the accents
 * on each letter's long-press. [Letters.build] turns it into keys.
 */
class LetterData(
    val language: String,
    val rows: List<String>,
    val accents: Map<Char, String>,
    /** Extra entries at the end of the period key's long-press list. */
    val periodExtra: List<String> = emptyList(),
)

object Letters {
    private val periodPopup = listOf(",", "?", "!", "'", "\"", ":", ";", "-", "/", "…")

    fun data(language: String): LetterData = when (language) {
        "de" -> LayoutGerman.data
        "es" -> LayoutSpanish.data
        else -> LayoutEnglish.data
    }

    /**
     * Letter keys get their corner symbol from [LongPressSymbols] by position, so every language
     * has the same symbol in the same spot, and their accents after it in the same popup.
     */
    fun build(data: LetterData, numberRow: Boolean): Layout {
        val lang = data.language
        val letterRows = data.rows.mapIndexed { r, row ->
            row.mapIndexed { c, ch ->
                val corner = LongPressSymbols.at(r, c, lang)
                val accents = data.accents[ch].orEmpty().map { it.toString() }
                Key(ch.toString(), corner = corner, popup = listOfNotNull(corner) + accents)
            }
        }
        val units = maxOf(10, data.rows.maxOf { it.length }).toFloat()
        val bottomLetters = letterRows[2]
        val side = (units - bottomLetters.size) / 2f
        val rows = ArrayList<List<Key>>()
        if (numberRow) rows += NumberRow.keys()
        rows += letterRows[0]
        rows += letterRows[1]
        rows += listOf(Key("", kind = Kind.SHIFT, width = side)) + bottomLetters + Key("", kind = Kind.BACKSPACE, width = side)
        rows += listOf(
            Key("?123", kind = Kind.SYMBOLS, width = 1.5f),
            Key(",", longPressSettings = true),
            Key("", " ", Kind.SPACE, width = units - 5f),
            Key(".", popup = periodPopup + data.periodExtra),
            Key("", "\n", Kind.ENTER, width = 1.5f),
        )
        return Layout("letters_$lang" + if (numberRow) "_num" else "", rows)
    }
}
