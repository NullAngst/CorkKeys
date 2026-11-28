/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

/**
 * One-handed mode: the keys shrink toward one side, with an arrow to flip sides and a button to
 * go back to full width in the space left over. Geometry.kt does the sizing; this decides which
 * side, and remembers the last side used.
 */
object OneHanded {
    fun isOn(m: Mode) = m == Mode.ONE_LEFT || m == Mode.ONE_RIGHT

    /** The tools row button: on to the last side used, or back to full width. */
    fun toggle(prefs: Prefs): Mode {
        val now = prefs.mode
        val next = if (isOn(now)) Mode.DOCKED else Mode.entries.firstOrNull { it.name == prefs.lastOneHanded && isOn(it) } ?: Mode.ONE_RIGHT
        set(prefs, next)
        return next
    }

    fun flip(prefs: Prefs): Mode {
        val next = if (prefs.mode == Mode.ONE_LEFT) Mode.ONE_RIGHT else Mode.ONE_LEFT
        set(prefs, next)
        return next
    }

    fun set(prefs: Prefs, m: Mode) {
        prefs.mode = m
        if (isOn(m)) prefs.lastOneHanded = m.name
    }
}
