/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import kotlin.math.abs

/**
 * Slide along the space bar to move the cursor. Once the finger has moved far enough sideways,
 * every [step] pixels moves the cursor one character, left or right.
 */
class SpaceCursor(private val startX: Float, private val step: Float) {
    var active = false
        private set

    private var anchor = startX

    /** Follows the finger. Returns how many characters to move: negative is left. */
    fun move(x: Float): Int {
        if (!active) {
            if (abs(x - startX) < step * START_STEPS) return 0
            active = true
            anchor = x
            return if (x > startX) 1 else -1
        }
        val n = ((x - anchor) / step).toInt()
        if (n == 0) return 0
        anchor += n * step
        return n
    }

    companion object {
        /** How many steps the finger moves before the cursor starts to follow. */
        const val START_STEPS = 1.5f
    }
}
