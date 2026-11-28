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

class PrefsTest {
    @Test
    fun aFreshInstallIsAPlainKeyboard() {
        val p = Prefs(MapKeyValues())
        assertFalse("suggestions", p.suggestions)
        assertFalse("autocorrect", p.autocorrect)
        assertFalse("swipe", p.swipe)
        assertFalse("learned words", p.learn)
        assertFalse("swear words", p.swearWords)
        assertFalse("number row", p.numberRow)
        assertFalse("suggestions in no-suggestion fields", p.inNoSuggestFields)
        assertFalse("incognito", p.incognito)
        assertTrue("vibration", p.vibrate)
        assertTrue("key bubbles", p.bubbles)
        assertTrue("double-space period", p.doubleSpace)
        assertTrue("automatic capitals", p.autoCaps)
        assertTrue("bordered keys", p.bordered)
        assertEquals(KeyHeight.NORMAL, p.height)
        assertEquals(Mode.DOCKED, p.mode)
        assertEquals("material", p.theme)
        assertEquals("", p.voiceIme)
    }

    @Test
    fun customHeight() {
        val p = Prefs(MapKeyValues())
        assertEquals(KeyHeight.NORMAL.dp, p.rowDp)
        p.height = KeyHeight.TALLER
        assertEquals(68f, p.rowDp)
        p.customRowDp = 80f
        p.heightRaw = Prefs.CUSTOM
        assertEquals(80f, p.rowDp)
        p.customRowDp = 500f
        assertEquals("kept inside the slider's range", Geometry.MAX_ROW_DP, p.rowDp)
    }

    @Test
    fun firstLanguageFollowsThePhone() {
        assertEquals(listOf("de"), Prefs(MapKeyValues(), "de").languages)
        assertEquals("es", Prefs(MapKeyValues(), "es").language)
        assertEquals(listOf("en"), Prefs(MapKeyValues(), "fr").languages)
    }

    @Test
    fun oneLanguageAlwaysStaysOn() {
        val p = Prefs(MapKeyValues())
        p.languages = emptyList()
        assertEquals(listOf("en"), p.languages)
        p.languages = listOf("es", "de")
        assertEquals("kept in the fixed order", listOf("de", "es"), p.languages)
        assertEquals("the current language moved to one that is on", "de", p.language)
        p.current = "es"
        p.languages = listOf("de")
        assertEquals("de", p.language)
    }

    @Test
    fun spaceBarLabel() {
        assertEquals("EN", Languages.spaceLabel("en", listOf("en", "de"), false))
        assertEquals("EN • DE", Languages.spaceLabel("en", listOf("en", "de"), true))
        assertEquals("the picked language comes first", "DE • EN • ES", Languages.spaceLabel("de", listOf("en", "de", "es"), true))
        assertEquals("ES", Languages.spaceLabel("es", listOf("es"), true))
    }
}
