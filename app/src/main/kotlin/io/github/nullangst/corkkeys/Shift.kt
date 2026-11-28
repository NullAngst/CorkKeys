/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

/**
 * Shift: tap for one capital, double-tap for caps lock, tap again to release. [AUTO] is the
 * capital the keyboard turns on by itself at the start of a sentence; it behaves like one
 * capital, and a tap turns it off.
 */
class Shift {
    enum class State { OFF, AUTO, ONE, LOCK }

    var state: State = State.OFF
        private set

    private var lastTap = NEVER

    val upper: Boolean get() = state != State.OFF
    val locked: Boolean get() = state == State.LOCK

    fun tap(now: Long) {
        val quick = lastTap != NEVER && now - lastTap <= DOUBLE_TAP_MS
        val next = when {
            state == State.LOCK -> State.OFF
            quick -> State.LOCK
            state == State.OFF -> State.ONE
            else -> State.OFF
        }
        // A double-tap has to start fresh after caps lock goes on or off.
        lastTap = if (state == State.LOCK || next == State.LOCK) NEVER else now
        state = next
    }

    /** A letter was typed: one capital is used up, caps lock stays. */
    fun typed() {
        if (state == State.ONE || state == State.AUTO) state = State.OFF
    }

    /** The start of a sentence, or not. Leaves a capital the user turned on alone. */
    fun auto(needed: Boolean) {
        if (state == State.OFF || state == State.AUTO) state = if (needed) State.AUTO else State.OFF
    }

    fun reset() {
        state = State.OFF
        lastTap = NEVER
    }

    companion object {
        const val DOUBLE_TAP_MS = 300L
        private const val NEVER = Long.MIN_VALUE
    }
}
