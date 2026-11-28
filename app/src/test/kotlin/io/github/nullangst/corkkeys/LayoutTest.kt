/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    private val languages = listOf("en", "de", "es")

    private fun all(): List<Layout> = languages.flatMap { lang ->
        listOf(
            Letters.build(Letters.data(lang), false),
            Letters.build(Letters.data(lang), true),
            LayoutSymbols.page1(lang),
            LayoutSymbols.page2(lang),
        )
    } + listOf(LayoutNumber.build(false), LayoutNumber.build(true), LayoutPhone.build())

    private fun letters(l: Layout) = l.rows.flatten().filter { it.isLetter }

    @Test
    fun everyKeyHasAnOutput() {
        for (l in all()) for (k in l.rows.flatten()) {
            if (k.kind == Kind.CHAR || k.kind == Kind.SPACE || k.kind == Kind.ENTER) {
                assertTrue("${l.id}: key '${k.label}' types nothing", k.output.isNotEmpty())
            }
        }
    }

    @Test
    fun noBlankLongPressEntries() {
        for (l in all()) for (k in l.rows.flatten()) for (p in k.popup) {
            assertTrue("${l.id}: key '${k.label}' has a blank long-press entry", p.isNotBlank())
        }
        for (lang in languages) for (k in letters(Letters.build(Letters.data(lang), false))) {
            for (p in Key.shiftPopup(k.popup)) assertTrue(p.isNotBlank())
        }
    }

    @Test
    fun cornerSymbolsFollowTheMap() {
        for (lang in languages) for (numberRow in listOf(false, true)) {
            val l = Letters.build(Letters.data(lang), numberRow)
            val first = if (numberRow) 1 else 0
            for (r in 0..2) {
                val row = l.rows[first + r].filter { it.isLetter }
                row.forEachIndexed { c, k ->
                    val want = LongPressSymbols.at(r, c, lang)
                    assertEquals("$lang row $r col $c", want, k.corner)
                    if (want != null) assertEquals("$lang: long-press on ${k.label} types the symbol", want, k.popup.first())
                }
            }
        }
    }

    @Test
    fun symbolsStayInPlaceAcrossLanguages() {
        // The key in the Y spot has ']' in English (y) and German (z).
        val en = Letters.build(LayoutEnglish.data, false).rows[0]
        val de = Letters.build(LayoutGerman.data, false).rows[0]
        assertEquals("y", en[5].label)
        assertEquals("z", de[5].label)
        assertEquals("]", en[5].corner)
        assertEquals("]", de[5].corner)
        assertEquals("w", en[1].label)
        assertEquals("\\", en[1].corner)
        assertNull("ü has no symbol", de[10].corner)
    }

    @Test
    fun gboardMap() {
        val l = Letters.build(LayoutEnglish.data, false)
        val corners = l.rows.take(3).flatten().filter { it.isLetter }.associate { it.label to it.corner }
        val expect = mapOf(
            "q" to "%", "w" to "\\", "e" to "|", "r" to "=", "t" to "[", "y" to "]", "u" to "<", "i" to ">", "o" to "{", "p" to "}",
            "a" to "@", "s" to "#", "d" to "$", "f" to "_", "g" to "&", "h" to "-", "j" to "+", "k" to "(", "l" to ")",
            "z" to "*", "x" to "\"", "c" to "'", "v" to ":", "b" to ";", "n" to "!", "m" to "?",
        )
        assertEquals(expect, corners)
    }

    @Test
    fun noDigitsOnLetterLongPress() {
        for (lang in languages) for (k in letters(Letters.build(Letters.data(lang), true))) {
            assertFalse("$lang ${k.label}", k.popup.any { it.length == 1 && it[0].isDigit() })
        }
    }

    @Test
    fun accentsAndSpecialLetters() {
        val de = Letters.build(LayoutGerman.data, false)
        val deLetters = letters(de).map { it.label }
        assertTrue(deLetters.containsAll(listOf("ü", "ö", "ä")))
        val s = letters(de).first { it.label == "s" }
        assertTrue("ß and ẞ on s", s.popup.containsAll(listOf("ß", "ẞ")))
        assertEquals("ẞ", Key.shiftText("ß"))

        val es = Letters.build(LayoutSpanish.data, false)
        assertTrue(letters(es).any { it.label == "ñ" })
        for (v in "aeiou") {
            val k = letters(es).first { it.label == v.toString() }
            assertTrue("accents on $v", k.popup.size > 1)
        }
        val n = letters(es).first { it.label == "n" }
        val m = letters(es).first { it.label == "m" }
        assertTrue("¡ within n's long-press", n.popup.containsAll(listOf("!", "¡")))
        assertTrue("¿ within m's long-press", m.popup.containsAll(listOf("?", "¿")))

        val en = Letters.build(LayoutEnglish.data, false)
        for (v in "aeiou") assertTrue(letters(en).first { it.label == v.toString() }.popup.size > 1)
    }

    @Test
    fun currencyFollowsTheLanguage() {
        fun cur(lang: String) = LayoutSymbols.page1(lang).rows[2][2].output
        assertEquals("$", cur("en"))
        assertEquals("€", cur("de"))
        assertEquals("€", cur("es"))
        assertEquals("€", Letters.build(LayoutGerman.data, false).rows[1][2].corner)
        assertTrue(LayoutSymbols.page2("en").rows.flatten().any { it.output == "€" })
        assertTrue(LayoutSymbols.page2("de").rows.flatten().any { it.output == "$" })
    }

    @Test
    fun symbolsPagesReachEachOther() {
        for (lang in languages) {
            val p1 = LayoutSymbols.page1(lang).rows.flatten().map { it.kind }
            val p2 = LayoutSymbols.page2(lang).rows.flatten().map { it.kind }
            assertTrue(Kind.SYMBOLS_MORE in p1)
            assertTrue(Kind.LETTERS in p1)
            assertTrue(Kind.SYMBOLS in p2)
            assertTrue(Kind.LETTERS in p2)
            val l = Letters.build(Letters.data(lang), false).rows.flatten().map { it.kind }
            assertTrue(Kind.SYMBOLS in l)
            val digits = LayoutSymbols.page1(lang).rows[0].map { it.output }
            assertEquals("1234567890".map { it.toString() }, digits)
            assertTrue(LayoutSymbols.page1(lang).rows.flatten().any { it.output == "/" })
        }
    }

    @Test
    fun bottomRowOrder() {
        for (lang in languages) {
            val bottom = Letters.build(Letters.data(lang), false).rows.last()
            assertEquals(listOf(Kind.SYMBOLS, Kind.CHAR, Kind.SPACE, Kind.CHAR, Kind.ENTER), bottom.map { it.kind })
            assertEquals(",", bottom[1].output)
            assertEquals("comma long-press offers settings, then emoji", listOf(Key.ACTION_SETTINGS, Key.ACTION_EMOJI), bottom[1].popup)
            assertEquals(":)", bottom[1].corner)
            assertEquals(".", bottom[3].output)
            val plain = Letters.build(Letters.data(lang), false, emojiComma = false).rows.last()
            assertTrue("emoji off: comma long-press opens settings", plain[1].longPressSettings)
            assertTrue(plain[1].popup.isEmpty())
        }
    }

    @Test
    fun symbolPagesShareTheComma() {
        for (lang in languages) {
            for (page in listOf(LayoutSymbols.page1(lang), LayoutSymbols.page2(lang))) {
                val comma = page.rows.last().first { it.output == "," }
                assertEquals(listOf(Key.ACTION_SETTINGS, Key.ACTION_EMOJI), comma.popup)
            }
            val off = LayoutSymbols.page1(lang, emojiComma = false).rows.last().first { it.output == "," }
            assertTrue(off.longPressSettings)
        }
    }

    @Test
    fun actionsAreNeverTyped() {
        assertTrue(Key.isAction(Key.ACTION_SETTINGS))
        assertTrue(Key.isAction(Key.ACTION_EMOJI))
        assertFalse(Key.isAction(","))
        // Private-use characters: no font draws them and no app expects them.
        for (a in listOf(Key.ACTION_SETTINGS, Key.ACTION_EMOJI)) assertEquals(Character.PRIVATE_USE.toInt(), Character.getType(a[0]))
    }

    @Test
    fun numberRowIsOptional() {
        for (lang in languages) {
            val off = Letters.build(Letters.data(lang), false)
            val on = Letters.build(Letters.data(lang), true)
            assertEquals(off.rows.size + 1, on.rows.size)
            assertEquals("1234567890".map { it.toString() }, on.rows[0].map { it.output })
        }
    }

    @Test
    fun rowsAddUpToTheLayoutWidth() {
        for (l in all()) {
            val units = l.units
            for (row in listOf(l.rows.last())) {
                assertEquals(l.id, units, row.sumOf { it.width.toDouble() }.toFloat(), 0.001f)
            }
        }
    }

    @Test
    fun padsHaveWhatTheirFieldsNeed() {
        val num = LayoutNumber.build(false).rows.flatten().map { it.output }
        assertTrue("0123456789".all { it.toString() in num })
        val dt = LayoutNumber.build(true).rows.flatten().map { it.output }
        assertTrue("/" in dt && ":" in dt)
        val phone = LayoutPhone.build().rows.flatten()
        assertTrue("0123456789*#".all { c -> phone.any { it.output == c.toString() } })
        assertTrue(phone.any { it.popup.contains("+") })
    }
}
