/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

/**
 * The small rules about spaces and punctuation: double-space period, the space after a
 * suggestion or a swiped word, and punctuation pulling that space back. Pure functions on the
 * text around the cursor, so each rule is tested on its own.
 */
object Punctuation {
    /** Two taps of space closer together than this make ". ". */
    const val DOUBLE_SPACE_MS = 600L

    /** Characters a sentence can end on for double-space period: closing brackets and quotes. */
    private const val CLOSERS = ")]}\"'”’»›"

    /** Punctuation that sits right after a word, pulling back a space the keyboard added. */
    private const val PULL_BACK = ".,!?:;)]}…%"

    /** After these, a swiped word goes in without a space first. */
    private const val NO_SPACE_AFTER = "([{\"'“‘„«‹¿¡/@#-_\n\t "

    fun endsSentence(c: Char): Boolean = c.isLetterOrDigit() || c in CLOSERS

    /**
     * Double-space period. [before] is the text before the cursor, ending in the first space;
     * [sinceLastSpace] the time since that space was typed. True when the second space should
     * turn the first into ". ".
     */
    fun doubleSpace(before: CharSequence?, sinceLastSpace: Long, field: FieldInfo, enabled: Boolean): Boolean {
        if (!enabled || !field.allowsDoubleSpace) return false
        if (sinceLastSpace < 0 || sinceLastSpace > DOUBLE_SPACE_MS) return false
        if (before == null || before.length < 2) return false
        if (before[before.length - 1] != ' ') return false
        return endsSentence(before[before.length - 2])
    }

    /** The keyboard added a space after a word, and [c] is typed next: should the space go? */
    fun pullsBack(c: Char): Boolean = c in PULL_BACK

    /** Picking a suggestion adds a space only when the text after the cursor doesn't start with one. */
    fun spaceAfterPick(after: CharSequence?): Boolean = after.isNullOrEmpty() || !after[0].isWhitespace()

    /** A swiped word gets a space before it unless the cursor is at the start or after an opener. */
    fun spaceBeforeSwipe(before: CharSequence?): Boolean {
        if (before.isNullOrEmpty()) return false
        return before[before.length - 1] !in NO_SPACE_AFTER
    }
}
