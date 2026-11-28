/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class DictionaryTest {
    private val languages = listOf("en", "de", "es")
    private val cap = 250_000

    private fun lines(lang: String) =
        GZIPInputStream(File(TestWords.dir, "$lang.txt.gz").inputStream()).bufferedReader(Charsets.UTF_8).readLines()

    @Test
    fun shippedListsAreWellFormed() {
        for (lang in languages) {
            val lines = lines(lang)
            assertTrue("$lang under its cap", lines.size <= cap)
            var last = Int.MAX_VALUE
            for ((i, line) in lines.withIndex()) {
                assertTrue("$lang line ${i + 1} is blank", line.isNotBlank())
                val parts = line.split('\t')
                assertEquals("$lang line ${i + 1}: three columns", 3, parts.size)
                val f = parts[1].toInt()
                assertTrue("$lang line ${i + 1}: frequency 0 to 255", f in 0..255)
                assertTrue("$lang line ${i + 1}: most common first", f <= last)
                assertTrue("$lang line ${i + 1}: flag", parts[2] == "o" || parts[2] == "-")
                last = f
            }
        }
    }

    @Test
    fun everyListLoads() {
        for (lang in languages) {
            val d = TestWords.get(lang)
            assertTrue(d.size > 100_000)
            assertTrue(d.find(d.word(0)) == 0)
            assertEquals(-1, d.find("zzqqxxnotaword"))
        }
    }

    @Test
    fun wordCountsMatchTheReadme() {
        val readme = File("../README.md").readText()
        for (lang in languages) {
            val count = lines(lang).size
            val shown = "%,d".format(java.util.Locale.US, count)
            assertTrue("README lists $lang with $shown words", readme.contains("| $lang | $shown |"))
        }
    }

    @Test
    fun extraListsMarkTheirWords() {
        for (lang in languages) {
            val d = TestWords.get(lang)
            val extra = File(TestWords.dir, "${lang}_extra.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            assertTrue(extra.isNotEmpty())
            for (line in extra) {
                val w = line.substringBefore('\t')
                val id = d.find(w)
                assertTrue("$lang: $w is in the list", id >= 0)
                assertTrue("$lang: $w is marked possibly offensive", d.offensive(id))
            }
        }
    }

    @Test
    fun trieAnswersExactly() {
        val d = Dictionary.build("t", sequenceOf("the\t200\t-", "they\t150\t-", "Haus\t120\t-", "damn\t80\to", "", "bad line", "x\tnotanumber\t-"))
        assertEquals(4, d.size)
        assertTrue(d.find("the") >= 0)
        assertTrue(d.find("they") >= 0)
        assertEquals(-1, d.find("th"))
        assertEquals(-1, d.find("haus"))
        assertTrue(d.offensive(d.find("damn")))
        assertFalse(d.offensive(d.find("the")))
        assertEquals(200, d.maxFrequency(0))
    }
}
