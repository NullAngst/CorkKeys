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

class CorrectorTest {
    private fun fix(c: Corrector, typed: String): String? = c.autocorrection(typed, c.suggest(typed, false))

    /** Typos and the fix autocorrect must make, per language. */
    private val typos = mapOf(
        "en" to mapOf(
            "teh" to "the", "thsi" to "this", "becuase" to "because", "recieve" to "receive",
            "definately" to "definitely", "helo" to "hello", "dont" to "don't", "im" to "I'm",
            "i" to "I", "wierd" to "weird", "iphone" to "iPhone", "adn" to "and",
        ),
        "de" to mapOf(
            "haus" to "Haus", "strasse" to "Straße", "muede" to "müde", "schoen" to "schön",
            "Fussball" to "Fußball", "nciht" to "nicht", "dsa" to "das", "ueber" to "über",
        ),
        "es" to mapOf(
            "espanol" to "español", "manana" to "mañana", "qeu" to "que", "nino" to "niño",
            "corazon" to "corazón", "tambien" to "también",
        ),
    )

    @Test
    fun typoTables() {
        for ((lang, table) in typos) {
            val c = TestWords.corrector(lang)
            for ((typed, want) in table) assertEquals("$lang: $typed", want, fix(c, typed))
        }
    }

    /** Real words are never replaced: correct words, German nouns, and swear words and slurs. */
    private val keep = mapOf(
        "en" to listOf("the", "hello", "their", "there", "cafe", "fuck", "fucking", "shit", "bitch", "cunt", "asshole", "bollocks", "crap", "nigger", "faggot", "retard"),
        "de" to listOf("Haus", "Straße", "danke", "schon", "Scheiße", "Arschloch", "Fotze", "Wichser", "Hurensohn", "Schwuchtel", "Kanake", "Spast"),
        "es" to listOf("gracias", "porque", "el", "mierda", "puta", "joder", "coño", "cabrón", "gilipollas", "pendejo", "maricón", "hostia"),
    )

    @Test
    fun realWordsAreNeverReplaced() {
        for ((lang, words) in keep) {
            val c = TestWords.corrector(lang)
            for (w in words) {
                assertTrue("$lang: $w is a real word", c.inList(w))
                assertNull("$lang: $w stays as typed", fix(c, w))
                assertNull("$lang: ${Chars.capitalize(w)} stays as typed", fix(c, Chars.capitalize(w)))
            }
        }
    }

    @Test
    fun swearWordSwitchOnlyDecidesTheStrip() {
        val swears = mapOf("en" to listOf("fuck", "shit", "cunt"), "de" to listOf("Arschloch", "Fotze"), "es" to listOf("mierda", "joder"))
        for ((lang, words) in swears) {
            val c = TestWords.corrector(lang)
            for (w in words) {
                val prefix = w.dropLast(1)
                assertFalse("$lang: $w hidden with the switch off", c.suggest(prefix, false, 12).any { it.word == w })
                assertFalse("$lang: $w hidden when typed in full too", c.suggest(w, false, 12).any { it.word == w })
                assertTrue("$lang: $w suggested with the switch on", c.suggest(w, true, 12).any { it.word == w })
                assertTrue("$lang: $w still a real word with the switch off", c.inList(w))
            }
        }
    }

    @Test
    fun germanNounsKeepTheirCapitals() {
        val c = TestWords.corrector("de")
        assertTrue(c.suggest("hau", false, 8).any { it.word == "Haus" })
        assertEquals("Haus", Chars.applyCase("hau", "Haus"))
        assertEquals("HAUS", Chars.applyCase("HAU", "Haus"))
        assertEquals("Haus", Chars.applyCase("Hau", "Haus"))
        assertEquals("caps lock", "HAUS", Chars.applyCase("hau", "Haus", capsLock = true))
    }

    @Test
    fun cheapMatches() {
        val en = TestWords.corrector("en")
        assertTrue(en.suggest("cafe", false).any { it.word == "café" })
        assertEquals("I'm", en.suggest("im", false).first().word)
        val es = TestWords.corrector("es")
        assertEquals("español", es.suggest("espanol", false).first().word)
    }

    @Test
    fun completionsStartWithWhatWasTyped() {
        val c = TestWords.corrector("en")
        val s = c.suggest("hel", false, 6)
        assertTrue(s.any { it.word == "help" })
        assertTrue(s.any { it.word == "hello" || it.word == "held" })
        assertNull("a prefix of a real word is never autocorrected", fix(c, "hel"))
    }

    @Test
    fun twoLanguagesAtOnce() {
        val mixed = TestWords.corrector("en", "de")
        assertTrue("German words while English is picked", mixed.suggest("schoen", false).any { it.word == "schön" })
        assertTrue("English still there", mixed.suggest("teh", false).first().word == "the")
        assertTrue(mixed.inList("Straße") && mixed.inList("house"))
        assertNull("a German word is real in a mixed sentence", fix(mixed, "danke"))
        // The same word, equally common in both lists: the picked language wins the tie.
        val a = Dictionary.build("en", sequenceOf("hand\t150\t-"))
        val b = Dictionary.build("de", sequenceOf("hand\t150\t-"))
        assertEquals("en", Corrector(listOf(a, b), null).suggest("hand", false).first().language)
        assertEquals("de", Corrector(listOf(b, a), null).suggest("hand", false).first().language)
    }

    @Test
    fun removedWordsAreNeverSuggested() {
        val c = Corrector(listOf(TestWords.get("en")), TestWords.keys("en"), removed = setOf("help"))
        assertFalse(c.suggest("hel", false, 8).any { it.word == "help" })
        assertTrue("still a real word", c.inList("help"))
    }

    @Test
    fun learnedWordsCountAsRealWords() {
        val mine = Dictionary.ofWords("user", listOf("corkkeys"), 120)
        val c = Corrector(listOf(TestWords.get("en")), TestWords.keys("en"), learned = mine)
        assertTrue(c.inList("corkkeys"))
        assertNull(fix(c, "corkkeys"))
        assertTrue(c.suggest("corkk", false).any { it.word == "corkkeys" })
    }

    @Test
    fun autocorrectMarginIsOneConstant() {
        // A made-up word far from anything common stays as typed.
        val c = TestWords.corrector("en")
        for (w in listOf("zqxv", "Kristensson", "bfgq", "xyzzy")) assertNull(w, fix(c, w))
        assertEquals(60f, Corrector.AUTOCORRECT_MARGIN)
    }
}
