/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.service.textservice.SpellCheckerService
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Android's spell checker service: red underlines and fixes in other apps, from the same word
 * lists and the same corrector as the keyboard. Android only sends it words after you pick
 * CorkKeys as the spell checker in Android's own settings. It checks and answers; it keeps
 * nothing and never adds to learned words.
 */
class SpellCheck : SpellCheckerService() {
    override fun createSession(): Session = Checker()

    private inner class Checker : Session() {
        private var lang = "en"
        private var keyMap: KeyMap? = null

        override fun onCreate() {
            val loc = locale.orEmpty().lowercase()
            lang = Languages.ALL.firstOrNull { loc.startsWith(it) } ?: "en"
            keyMap = KeyMap.of(Letters.build(Letters.data(lang), false))
            inUse += lang
            Dictionaries.load(this@SpellCheck, listOf(lang)) {}
        }

        override fun onClose() {
            inUse -= lang
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            val word = textInfo?.text?.trim().orEmpty()
            // Nothing to judge, or the list isn't loaded yet: say it's fine rather than guess.
            if (word.isEmpty() || word.none { it.isLetter() } || word.any { it.isDigit() }) return known()
            val d = Dictionaries.get(lang) ?: return known()
            val c = Corrector(listOf(d), keyMap)
            if (c.inList(word)) return known()
            val swear = try {
                Prefs.open(this@SpellCheck).swearWords
            } catch (_: Exception) {
                false
            }
            val fixes = c.suggest(word, swear, suggestionsLimit.coerceIn(1, 5))
                .map { Chars.applyCase(word, it.word) }
                .distinct()
                .toTypedArray()
            val attrs = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or
                (if (fixes.isNotEmpty()) SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS else 0)
            return SuggestionsInfo(attrs, fixes)
        }

        private fun known() = SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, arrayOf())
    }

    companion object {
        /** Languages an open spell checker session needs, so the keyboard doesn't unload them. */
        val inUse: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}
