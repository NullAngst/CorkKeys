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

class FieldTest {
    // android.text.InputType values, spelled out since the stub android.jar is all there is here.
    private val text = 0x1
    private val passwords = listOf(text or 0x80, text or 0x90, text or 0xe0, 0x2 or 0x10)
    private val noLearningFlag = 0x1000000

    @Test
    fun passwordFieldsSwitchEverythingOff() {
        for (t in passwords) {
            val f = Field.read(t, 0)
            assertTrue(f.password)
            assertFalse(f.allowsSuggestions(true))
            assertFalse(f.allowsAutocorrect)
            assertFalse(f.allowsSwipe)
            assertFalse(f.allowsLearning)
            assertFalse(f.allowsBubbles)
            assertFalse(f.allowsDoubleSpace)
        }
    }

    @Test
    fun incognitoFieldsLearnNothing() {
        val f = Field.read(text, noLearningFlag)
        assertFalse(f.allowsLearning)
        assertTrue("suggestions still work there", f.allowsSuggestions(false))
    }

    @Test
    fun appsCanAskForIncognito() {
        // Signal's incognito keyboard, Firefox private tabs: IME_FLAG_NO_PERSONALIZED_LEARNING.
        assertTrue(Field.read(text, noLearningFlag).incognito(false))
        assertTrue("switched on by hand", Field.read(text, 0).incognito(true))
        assertFalse(Field.read(text, 0).incognito(false))
    }

    @Test
    fun noSuggestionsFlagAndItsSwitch() {
        val f = Field.read(text or 0x80000, 0)
        assertFalse(f.allowsSuggestions(false))
        assertTrue(f.allowsSuggestions(true))
        assertFalse("never in password fields", Field.read(text or 0x80 or 0x80000, 0).allowsSuggestions(true))
    }

    @Test
    fun swipeOnlyInPlainText() {
        assertTrue(Field.read(text, 0).allowsSwipe)
        for (t in listOf(text or 0x10, text or 0x20, text or 0xd0, 0x2, 0x3, 0x4, 0x0) + passwords) {
            assertFalse("input type $t", Field.read(t, 0).allowsSwipe)
        }
    }

    @Test
    fun everyFieldTypeGetsALayout() {
        val cases = mapOf(
            text to "letters_en",
            text or 0x10 to "letters_en",
            text or 0x20 to "letters_en",
            0x2 to "number",
            0x2 or 0x2000 to "number",
            0x2 or 0x10 to "number",
            0x3 to "phone",
            0x4 to "number_datetime",
            0x4 or 0x10 to "number_datetime",
            0x4 or 0x20 to "number_datetime",
            0x0 to "letters_en",
        )
        for ((t, id) in cases) {
            assertEquals("input type $t", id, Field.layoutFor(Field.read(t, 0).kind, "en", false).id)
        }
        // Every kind has a layout, whatever new ones get added.
        for (k in FieldKind.entries) Field.layoutFor(k, "de", true)
    }

    @Test
    fun enterShowsTheAction() {
        assertEquals(Enter.SEARCH, Field.read(text, 3).enter)
        assertEquals(Enter.SEND, Field.read(text, 4).enter)
        assertEquals(Enter.NEXT, Field.read(text, 5).enter)
        assertEquals(Enter.DONE, Field.read(text, 6).enter)
        assertEquals(Enter.GO, Field.read(text, 2).enter)
        assertEquals(Enter.NEWLINE, Field.read(text, 0).enter)
        assertEquals("multi-line fields that say no enter action", Enter.NEWLINE, Field.read(text, 4 or 0x40000000).enter)
    }
}
