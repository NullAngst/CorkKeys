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

class LearnedWordsTest {
    private class MemoryStore : WordStore {
        var saved: List<String> = emptyList()
        var writes = 0
        override fun read() = saved
        override fun write(words: Collection<String>) {
            saved = words.toList()
            writes++
        }
    }

    // What the keyboard passes as "allowed": the switch, incognito and the field together.
    private fun allowed(learn: Boolean, incognito: Boolean, inputType: Int, imeOptions: Int = 0) =
        learn && !incognito && Field.read(inputType, imeOptions).allowsLearning

    @Test
    fun keptAfterTwoFullTypings() {
        val s = MemoryStore()
        val l = LearnedWords(s)
        assertFalse(l.typedInFull("Kristensson", true, false))
        assertTrue(s.saved.isEmpty())
        assertTrue(l.typedInFull("Kristensson", true, false))
        assertEquals("only the word is stored", listOf("Kristensson"), s.saved)
        assertFalse("already learned", l.typedInFull("Kristensson", true, false))
    }

    @Test
    fun neverWithTheSwitchOff() {
        val s = MemoryStore()
        val l = LearnedWords(s)
        repeat(5) { l.typedInFull("Zhai", allowed(false, false, 0x1), false) }
        assertTrue(s.saved.isEmpty())
        assertEquals(0, s.writes)
    }

    @Test
    fun neverInPasswordOrIncognitoFields() {
        val s = MemoryStore()
        val l = LearnedWords(s)
        for (t in listOf(0x1 or 0x80, 0x1 or 0x90, 0x1 or 0xe0, 0x2 or 0x10)) {
            repeat(3) { l.typedInFull("hunter2x", allowed(true, false, t), false) }
        }
        repeat(3) { l.typedInFull("Zhai", allowed(true, false, 0x1, 0x1000000), false) }
        repeat(3) { l.typedInFull("Zhai", allowed(true, true, 0x1), false) }
        assertTrue(s.saved.isEmpty())
        assertEquals(0, s.writes)
    }

    @Test
    fun aBlockedTypingDoesNotCountTowardTwo() {
        val l = LearnedWords(MemoryStore())
        assertFalse(l.typedInFull("Zhai", true, false))
        assertFalse(l.typedInFull("Zhai", false, false))
        assertFalse("the earlier count was dropped", l.typedInFull("Zhai", true, false))
        assertTrue(l.typedInFull("Zhai", true, false))
    }

    @Test
    fun knownWordsAndJunkAreSkipped() {
        val s = MemoryStore()
        val l = LearnedWords(s)
        repeat(3) { l.typedInFull("hello", true, true) }
        repeat(3) { l.typedInFull("a", true, false) }
        repeat(3) { l.typedInFull("abc123", true, false) }
        assertTrue(s.saved.isEmpty())
    }

    @Test
    fun deleteAndClear() {
        val s = MemoryStore()
        val l = LearnedWords(s)
        repeat(2) { l.typedInFull("Zhai", true, false) }
        repeat(2) { l.typedInFull("Kristensson", true, false) }
        l.remove("Zhai")
        assertEquals(listOf("Kristensson"), s.saved)
        l.clear()
        assertTrue(s.saved.isEmpty())
        assertTrue(l.all().isEmpty())
    }

    @Test
    fun removedWordsStayRemoved() {
        val s = MemoryStore()
        val r = RemovedWords(s)
        r.add("Help")
        assertEquals(listOf("help"), s.saved)
        assertTrue("help" in r.all())
        r.remove("help")
        assertTrue(s.saved.isEmpty())
    }
}
