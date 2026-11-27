/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

/**
 * Backspace by word. Slide left from backspace and whole words get selected, one per step of
 * slide; slide back right and they drop out of the selection again. Lifting deletes what is
 * selected. Nothing is deleted until the finger lifts, which is what makes sliding back work.
 */
class BackspaceSlide(private val startX: Float, private val step: Float) {
    var active = false
        private set

    /** Words selected right now. */
    var words = 0
        private set

    /** Follows the finger. Returns true when the number of words changed. */
    fun move(x: Float): Boolean {
        val dx = startX - x
        if (!active) {
            if (dx < step * START_FRACTION) return false
            active = true
        }
        val n = if (dx <= 0f) 0 else 1 + ((dx - step * START_FRACTION) / step).toInt().coerceAtLeast(0)
        if (n == words) return false
        words = n
        return true
    }

    companion object {
        /** How far, in steps, the finger moves left before the slide starts. */
        const val START_FRACTION = 0.6f

        private fun wordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’' || c == '_'

        /**
         * How many characters at the end of [before] make up its last [words] words. A word is a
         * run of letters (with apostrophes), or a run of punctuation, plus the spaces after it.
         */
        fun charsFor(before: CharSequence, words: Int): Int {
            var i = before.length
            repeat(words) {
                while (i > 0 && before[i - 1].isWhitespace()) i--
                if (i == 0) return before.length
                val wordy = wordChar(before[i - 1])
                while (i > 0 && !before[i - 1].isWhitespace() && wordChar(before[i - 1]) == wordy) i--
            }
            return before.length - i
        }
    }
}
