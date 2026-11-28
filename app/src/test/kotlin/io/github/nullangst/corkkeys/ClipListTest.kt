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

class ClipListTest {
    private val hour = ClipList.EXPIRY_MS

    @Test
    fun newestFirstAndNoDuplicates() {
        val l = ClipList(5)
        l.add("a", 1)
        l.add("b", 2)
        l.add("a", 3)
        assertEquals(listOf("a", "b"), l.items.map { it.text })
        assertFalse("blank copies are skipped", l.add("  ", 4))
    }

    @Test
    fun unpinnedGoAfterAnHourPinnedStay() {
        val l = ClipList(5)
        l.add("old", 0)
        l.add("pinned", 0)
        l.pin(l.items.first { it.text == "pinned" }, true)
        l.add("new", hour)
        assertEquals(listOf("pinned", "new", "old"), l.shown(hour).map { it.text })
        assertEquals(listOf("pinned", "new"), l.shown(hour + 1).map { it.text })
        assertEquals(listOf("pinned"), l.shown(hour * 3).map { it.text })
    }

    @Test
    fun copyingAPinnedClipAgainKeepsItPinned() {
        val l = ClipList(5)
        l.add("x", 0)
        l.pin(l.items[0], true)
        l.add("x", 10)
        assertTrue(l.items.single().pinned)
        assertEquals(10L, l.items.single().time)
    }

    @Test
    fun savesTheNewestFewPlusEveryPin() {
        val l = ClipList(2)
        for (i in 1..6) l.add("c$i", i.toLong())
        l.pin(l.items.first { it.text == "c1" }, true)
        assertEquals(listOf("c1", "c6", "c5"), l.toSave().map { it.text })
        l.keep = 0
        assertEquals(listOf("c1"), l.toSave().map { it.text })
    }

    @Test
    fun memoryIsCapped() {
        val l = ClipList(5)
        l.add("keep me", 0)
        l.pin(l.items[0], true)
        for (i in 0 until ClipList.MAX_IN_MEMORY + 20) l.add("c$i", i.toLong())
        assertEquals(ClipList.MAX_IN_MEMORY, l.items.count { !it.pinned })
        assertTrue(l.items.any { it.pinned && it.text == "keep me" })
        assertEquals("c${ClipList.MAX_IN_MEMORY + 19}", l.items[0].text)
    }

    @Test
    fun clearLeavesPins() {
        val l = ClipList(5)
        l.add("a", 0)
        l.add("b", 0)
        l.pin(l.items[0], true)
        l.clearUnpinned()
        assertEquals(listOf("b"), l.items.map { it.text })
    }

    @Test
    fun codecRoundTripsAnyText() {
        val clips = listOf(
            Clip("two\nlines\tand a tab", 123, pinned = true),
            Clip("ümlaut, emoji 👋🏽, and \\ slashes", 456),
        )
        val back = ClipList.decode(ClipList.encode(clips))
        assertEquals(clips.map { it.text }, back.map { it.text })
        assertEquals(clips.map { it.time }, back.map { it.time })
        assertEquals(clips.map { it.pinned }, back.map { it.pinned })
    }

    @Test
    fun badLinesAreSkipped() {
        val good = ClipList.encode(listOf(Clip("ok", 1)))
        val back = ClipList.decode("junk\n-\tnot a time\tAA==\np\t5\t%%%\n$good")
        assertEquals(listOf("ok"), back.map { it.text })
        assertTrue(ClipList.decode("").isEmpty())
    }
}
