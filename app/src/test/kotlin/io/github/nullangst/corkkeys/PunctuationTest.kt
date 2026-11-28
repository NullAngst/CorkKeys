/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctuationTest {
    private val text = Field.read(0x1, 0)
    private val quick = 200L

    @Test
    fun doubleSpaceAfterWordsDigitsAndClosers() {
        for (before in listOf("hello ", "2026 ", "(this) ", "\"quote\" ", "it’ ", "x ")) {
            assertTrue(before, Punctuation.doubleSpace(before, quick, text, true))
        }
    }

    @Test
    fun doubleSpaceSkippedAfterPunctuationAndSpaces() {
        for (before in listOf("hello. ", "what? ", "wait, ", "  ", " ", "", "word  ", "a-b- ")) {
            assertFalse(before, Punctuation.doubleSpace(before, quick, text, true))
        }
        assertFalse(Punctuation.doubleSpace(null, quick, text, true))
    }

    @Test
    fun doubleSpaceNeedsTwoQuickTaps() {
        assertFalse(Punctuation.doubleSpace("hello ", Punctuation.DOUBLE_SPACE_MS + 1, text, true))
        assertFalse(Punctuation.doubleSpace("hello ", -1, text, true))
    }

    @Test
    fun doubleSpaceSwitchAndFields() {
        assertFalse("switched off", Punctuation.doubleSpace("hello ", quick, text, false))
        val quiet = listOf(
            0x1 or 0x10, // URL
            0x1 or 0x20, // email
            0x1 or 0xd0, // web email
            0x1 or 0x80, // password
            0x1 or 0x90, // visible password
            0x1 or 0xe0, // web password
            0x2, // number
            0x2 or 0x10, // number password
            0x3, // phone
            0x4, // date and time
            0x0, // raw key events
        )
        for (t in quiet) assertFalse("input type $t", Punctuation.doubleSpace("hello ", quick, Field.read(t, 0), true))
    }

    @Test
    fun spaceAfterAPick() {
        assertTrue(Punctuation.spaceAfterPick(""))
        assertTrue(Punctuation.spaceAfterPick(null))
        assertTrue(Punctuation.spaceAfterPick("next"))
        assertTrue(Punctuation.spaceAfterPick(","))
        assertFalse("there is one already", Punctuation.spaceAfterPick(" next"))
        assertFalse(Punctuation.spaceAfterPick("\nnext"))
    }

    @Test
    fun punctuationPullsTheSpaceBack() {
        for (c in ".,!?:;)") assertTrue("$c", Punctuation.pullsBack(c))
        for (c in "a1(\"'-@") assertFalse("$c", Punctuation.pullsBack(c))
    }

    @Test
    fun swipedWordsGetASpaceBetweenThem() {
        assertTrue(Punctuation.spaceBeforeSwipe("hello"))
        assertTrue(Punctuation.spaceBeforeSwipe("end."))
        assertFalse(Punctuation.spaceBeforeSwipe(""))
        assertFalse(Punctuation.spaceBeforeSwipe(null))
        assertFalse(Punctuation.spaceBeforeSwipe("hello "))
        assertFalse(Punctuation.spaceBeforeSwipe("line\n"))
        assertFalse(Punctuation.spaceBeforeSwipe("¿"))
        assertFalse(Punctuation.spaceBeforeSwipe("("))
    }
}
