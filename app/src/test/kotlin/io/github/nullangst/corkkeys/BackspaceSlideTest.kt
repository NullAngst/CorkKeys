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

class BackspaceSlideTest {
    @Test
    fun wordsCountFromTheEnd() {
        val t = "Hello there, world it's me "
        assertEquals("me ".length, BackspaceSlide.charsFor(t, 1))
        assertEquals("it's me ".length, BackspaceSlide.charsFor(t, 2))
        assertEquals("world it's me ".length, BackspaceSlide.charsFor(t, 3))
        assertEquals("punctuation is its own word", ", world it's me ".length, BackspaceSlide.charsFor(t, 4))
        assertEquals(t.length, BackspaceSlide.charsFor(t, 99))
        assertEquals(0, BackspaceSlide.charsFor(t, 0))
        assertEquals(0, BackspaceSlide.charsFor("", 3))
    }

    @Test
    fun slideLeftSelectsAndSlideBackRestores() {
        val step = 100f
        val s = BackspaceSlide(1000f, step)
        assertFalse(s.move(990f))
        assertFalse(s.active)
        assertTrue(s.move(1000f - step * BackspaceSlide.START_FRACTION - 1f))
        assertTrue(s.active)
        assertEquals(1, s.words)
        s.move(1000f - step * BackspaceSlide.START_FRACTION - step * 2 - 1f)
        assertEquals(3, s.words)
        s.move(1000f - step * BackspaceSlide.START_FRACTION - step * 0.5f)
        assertEquals(1, s.words)
        s.move(1010f)
        assertEquals("all the way back puts every word back", 0, s.words)
        assertTrue("still sliding until the finger lifts", s.active)
    }

    @Test
    fun spaceBarCursor() {
        val c = SpaceCursor(500f, 10f)
        assertEquals(0, c.move(505f))
        assertEquals(1, c.move(516f))
        assertEquals(2, c.move(537f))
        assertEquals(-3, c.move(505f))
    }
}
