/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmojiDataTest {
    private fun real() = File("src/main/assets/emoji.txt").bufferedReader(Charsets.UTF_8).use { EmojiData.parse(it.lineSequence()) }

    @Test
    fun parsesGroupsNamesAndTones() {
        val text = """
            % Emoji 17.0
            % group smileys
            😀	grinning face	
            % group people
            👋	waving hand	👋🏻 👋🏼 👋🏽 👋🏾 👋🏿
            % group flags
            #️⃣	keycap: #	
        """.trimIndent()
        val g = EmojiData.parse(text.lineSequence())
        assertEquals(listOf("smileys", "people", "flags"), g.map { it.id })
        assertEquals("grinning face", g[0].entries[0].name)
        assertTrue(g[0].entries[0].tones.isEmpty())
        assertEquals(5, g[1].entries[0].tones.size)
        assertEquals("#️⃣", g[2].entries[0].emoji)
    }

    @Test
    fun theShippedListHasEveryGroupWithATabIcon() {
        val g = real()
        assertEquals(EmojiData.icons.keys.toList(), g.map { it.id })
        assertTrue("over 1800 emoji", g.sumOf { it.entries.size } > 1800)
        assertTrue("skin tones are there", g.flatMap { it.entries }.count { it.tones.size == 5 } > 250)
        // Tone variants are never listed again on their own.
        val bases = g.flatMap { it.entries }.map { it.emoji }.toSet()
        assertTrue(g.flatMap { it.entries }.flatMap { it.tones }.none { it in bases })
    }

    @Test
    fun recentsAreNewestFirstAndCapped() {
        val r = EmojiRecents()
        for (i in 0 until EmojiRecents.MAX + 5) r.used("e$i")
        r.used("e10")
        assertEquals("e10", r.list[0])
        assertEquals(EmojiRecents.MAX, r.list.size)
        assertEquals(1, r.list.count { it == "e10" })
    }

    @Test
    fun aPickedToneSticks() {
        val r = EmojiRecents()
        assertEquals("👋", r.toned("👋"))
        r.pickTone("👋", "👋🏽")
        assertEquals("👋🏽", r.toned("👋"))
        r.pickTone("👋", "👋")
        assertEquals("👋", r.toned("👋"))
    }
}
