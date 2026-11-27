/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import io.github.nullangst.corkkeys.Shift.State
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftTest {
    @Test
    fun tapGivesOneCapital() {
        val s = Shift()
        s.tap(0)
        assertEquals(State.ONE, s.state)
        s.typed()
        assertEquals(State.OFF, s.state)
    }

    @Test
    fun tapAgainReleases() {
        val s = Shift()
        s.tap(0)
        s.tap(1000)
        assertEquals(State.OFF, s.state)
    }

    @Test
    fun doubleTapLocksAndTapReleases() {
        val s = Shift()
        s.tap(0)
        s.tap(200)
        assertEquals(State.LOCK, s.state)
        s.typed()
        s.typed()
        assertEquals("caps lock survives typing", State.LOCK, s.state)
        s.tap(250)
        assertEquals("one tap, even a quick one, releases caps lock", State.OFF, s.state)
        s.tap(5000)
        assertEquals(State.ONE, s.state)
    }

    @Test
    fun autoCapitalAtSentenceStart() {
        val s = Shift()
        s.auto(true)
        assertEquals(State.AUTO, s.state)
        s.typed()
        assertEquals(State.OFF, s.state)
        s.auto(true)
        s.tap(0)
        assertEquals("a tap turns the automatic capital off", State.OFF, s.state)
        s.tap(100)
        assertEquals("and a double-tap from there locks", State.LOCK, s.state)
        s.auto(false)
        assertEquals("the keyboard never undoes caps lock", State.LOCK, s.state)
    }

    @Test
    fun autoLeavesAUserCapitalAlone() {
        val s = Shift()
        s.tap(0)
        s.auto(false)
        assertEquals(State.ONE, s.state)
    }

    @Test
    fun slowTapsNeverLock() {
        val s = Shift()
        var t = 0L
        repeat(6) {
            s.tap(t)
            t += Shift.DOUBLE_TAP_MS + 1
        }
        assertEquals(State.OFF, s.state)
    }
}
