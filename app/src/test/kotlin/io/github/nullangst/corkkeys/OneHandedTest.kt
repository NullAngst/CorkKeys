/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import org.junit.Assert.assertEquals
import org.junit.Test

class OneHandedTest {
    @Test
    fun toggleRemembersTheLastSide() {
        val p = Prefs(MapKeyValues())
        assertEquals(Mode.ONE_RIGHT, OneHanded.toggle(p))
        assertEquals(Mode.ONE_LEFT, OneHanded.flip(p))
        assertEquals(Mode.DOCKED, OneHanded.toggle(p))
        assertEquals("back on the side last used", Mode.ONE_LEFT, OneHanded.toggle(p))
        assertEquals(Mode.ONE_RIGHT, OneHanded.flip(p))
    }

    @Test
    fun floatingIsItsOwnMode() {
        val p = Prefs(MapKeyValues())
        OneHanded.set(p, Mode.FLOATING)
        assertEquals(Mode.FLOATING, p.mode)
        assertEquals("one-handed from floating goes to the remembered side", Mode.ONE_RIGHT, OneHanded.toggle(p))
    }
}
