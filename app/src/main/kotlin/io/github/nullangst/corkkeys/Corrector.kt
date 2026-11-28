/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import java.text.Normalizer
import java.util.PriorityQueue

/** Letters compared the way typos happen: without case, without accents. */
object Chars {
    private val table = CharArray(0x250) { i ->
        val c = i.toChar()
        when (c) {
            'ß', 'ẞ' -> 's'
            'æ', 'Æ' -> 'a'
            'œ', 'Œ', 'ø', 'Ø' -> 'o'
            'đ', 'Đ' -> 'd'
            'ł', 'Ł' -> 'l'
            else -> {
                val d = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                (if (d.isNotEmpty()) d[0] else c).lowercaseChar()
            }
        }
    }

    /** Lower case, accent dropped: é is e, Ü is u, ß is s. */
    fun base(c: Char): Char = if (c.code < table.size) table[c.code] else c.lowercaseChar()

    fun capitalize(s: String): String = if (s.isEmpty()) s else Key.shiftText(s.substring(0, 1)) + s.substring(1)

    /**
     * Gives a suggestion the case of what was typed: all capitals with caps lock or an
     * all-capitals word, a capital first letter after shift, and otherwise the list's own
     * capitals, so German nouns keep theirs.
     */
    fun applyCase(typed: String, word: String, capsLock: Boolean = false): String {
        val letters = typed.filter { it.isLetter() }
        return when {
            capsLock || (letters.length > 1 && letters.all { it.isUpperCase() }) -> word.map { Key.shiftText(it.toString()) }.joinToString("")
            typed.isNotEmpty() && typed[0].isUpperCase() -> capitalize(word)
            else -> word
        }
    }
}

/** A word the strip can offer, with its score. Corrections cover the whole typed word; completions extend it. */
class Candidate(val word: String, val score: Float, val correction: Boolean, val offensive: Boolean, val language: String)

/**
 * Suggestions and autocorrect from the loaded word lists, all on the phone.
 *
 * Completions are words that start with what was typed, most frequent first. Corrections come
 * from one walk of the trie that allows a few edits: a letter added, dropped, changed, or two
 * letters swapped; one edit for words up to four letters, two above that. A changed letter costs
 * less when the two keys are neighbours on screen. Accents, a missing apostrophe and German
 * ae/oe/ue/ss cost almost nothing. Ranking is frequency minus edit cost.
 *
 * Autocorrect only ever replaces a word that is in none of the loaded lists, and only when the
 * best correction wins by [AUTOCORRECT_MARGIN]. A word in a list is never replaced, which is what
 * keeps swear words and slurs exactly as typed.
 */
class Corrector(
    /** The language in use first, then any others with two languages at once. */
    private val dicts: List<Dictionary>,
    private val keys: KeyMap?,
    private val removed: Set<String> = emptySet(),
    private val learned: Dictionary? = null,
) {
    private val all: List<Dictionary> = dicts + listOfNotNull(learned)

    /** Forms of a typed word that count as the same word: "The" at a sentence start is "the". */
    private fun forms(typed: String): List<String> {
        val out = ArrayList<String>(3)
        out += typed
        if (typed.isNotEmpty() && typed[0].isUpperCase()) {
            out += typed[0].lowercaseChar() + typed.substring(1)
            val letters = typed.filter { it.isLetter() }
            if (letters.length > 1 && letters.all { it.isUpperCase() }) {
                val lower = typed.lowercase()
                out += lower
                out += Chars.capitalize(lower)
            }
        }
        return out
    }

    /** Whether the typed word is a real word in any loaded list, the user's learned words included. */
    fun inList(typed: String): Boolean = forms(typed).any { f -> all.any { it.find(f) >= 0 } }

    private fun isRemoved(w: String) = removed.isNotEmpty() && (w in removed || w.lowercase() in removed)

    fun suggest(typed: String, offensiveOk: Boolean, limit: Int = 4): List<Candidate> {
        if (typed.isEmpty() || typed.length > MAX_TYPED) return emptyList()
        val t = typed.toCharArray()
        val maxCost = if (t.size <= 4) 1f else 2f
        val best = HashMap<String, Candidate>()
        // For a word that is already real, completions are more use than its cheap variants.
        val tiered = !inList(typed)
        all.forEachIndexed { index, d ->
            val bonus = if (index == 0) CURRENT_LANGUAGE_BONUS else 0f
            Walk(d, t, maxCost, keys).run { id, cost, correction ->
                val w = d.word(id)
                val off = d.offensive(id)
                if ((off && !offensiveOk) || isRemoved(w)) return@run
                // Matches that differ only cheaply (case, accents, an apostrophe, ae/ss, a doubled
                // letter) rank above every real edit: "haus" is "Haus" before it is "aus".
                val tier = if (tiered && correction && cost <= CHEAP_TIER) CHEAP_TIER_BONUS else 0f
                val score = d.frequency(id) - EDIT_WEIGHT * cost + bonus + tier
                val key = w.lowercase()
                val prev = best[key]
                if (prev == null || prefer(typed, w, score, prev)) best[key] = Candidate(w, score, correction, off, d.language)
            }
        }
        return best.values.sortedByDescending { it.score }.take(limit)
    }

    /** Between two case forms of one word, the one matching how it was typed wins. */
    private fun prefer(typed: String, w: String, score: Float, prev: Candidate): Boolean {
        val lowerTyped = typed[0].isLowerCase()
        val wLower = w[0].isLowerCase()
        val pLower = prev.word[0].isLowerCase()
        if (wLower != pLower && w.equals(prev.word, ignoreCase = true)) return wLower == lowerTyped
        return score > prev.score
    }

    /**
     * What autocorrect would put in place of [typed], or null to leave it. [candidates] is a
     * [suggest] result for the same word, so the walk isn't run twice.
     */
    fun autocorrection(typed: String, candidates: List<Candidate>): String? {
        if (typed.any { it.isDigit() } || inList(typed)) return null
        val best = candidates.firstOrNull { it.correction } ?: return null
        if (best.word == typed) return null
        // One letter is only ever fixed in case: i becomes I.
        if (typed.length < 2 && !best.word.equals(typed, ignoreCase = true)) return null
        return if (best.score >= AUTOCORRECT_MARGIN) best.word else null
    }

    /** One walk of one trie against the typed word, a Damerau-Levenshtein row per node. */
    private class Walk(val d: Dictionary, val t: CharArray, val maxCost: Float, val keys: KeyMap?) {
        private val n = t.size
        private val rows = ArrayList<FloatArray>()
        private lateinit var emit: (Int, Float, Boolean) -> Unit

        private fun row(depth: Int): FloatArray {
            while (rows.size <= depth) rows += FloatArray(n + 1)
            return rows[depth]
        }

        fun run(out: (id: Int, cost: Float, correction: Boolean) -> Unit) {
            emit = out
            val r0 = row(0)
            r0[0] = 0f
            for (j in 1..n) r0[j] = r0[j - 1] + delCost(j)
            var c = d.child(0)
            while (c >= 0) {
                visit(c, 1, '\u0000')
                c = d.sibling(c)
            }
        }

        private fun visit(node: Int, depth: Int, parentChar: Char) {
            val ch = d.char(node)
            val p = rows[depth - 1]
            val g = if (depth >= 2) rows[depth - 2] else null
            val r = row(depth)
            val ins = if (ch == parentChar) DOUBLE else insCost(ch)
            r[0] = p[0] + ins
            var min = r[0]
            for (j in 1..n) {
                val tc = t[j - 1]
                var v = p[j - 1] + subCost(tc, ch)
                v = minOf(v, p[j] + ins, r[j - 1] + delCost(j))
                if (j >= 2 && g != null && same(t[j - 1], parentChar) && same(t[j - 2], ch) && !same(tc, ch)) {
                    v = minOf(v, g[j - 2] + TRANSPOSE)
                }
                if (j >= 2 && digraph(t[j - 2], tc, ch)) v = minOf(v, p[j - 2] + CHEAP)
                r[j] = v
                if (v < min) min = v
            }
            if (min > maxCost) return
            val full = r[n]
            val word = d.wordAt(node)
            if (word >= 0 && full <= maxCost) emit(word, full, true)
            if (full <= COMPLETE_MAX && p[n] > COMPLETE_MAX) complete(node, depth, full)
            var c = d.child(node)
            while (c >= 0) {
                visit(c, depth + 1, ch)
                c = d.sibling(c)
            }
        }

        /** The most frequent words below [start], best first, guided by each node's highest frequency. */
        private fun complete(start: Int, depth: Int, prefixCost: Float) {
            val q = PriorityQueue<Long>()
            fun push(pri: Int, isWord: Boolean, id: Int, len: Int) {
                q.add(((255L - pri) shl 40) or ((if (isWord) 1L else 0L) shl 39) or (len.toLong() shl 31) or id.toLong())
            }
            var c = d.child(start)
            while (c >= 0) {
                push(d.maxFrequency(c), false, c, depth + 1)
                c = d.sibling(c)
            }
            var found = 0
            var steps = 0
            while (q.isNotEmpty() && found < COMPLETIONS && steps++ < 4000) {
                val e = q.poll()
                val id = (e and 0x7FFFFFFF).toInt()
                val len = ((e shr 31) and 0xFF).toInt()
                if ((e shr 39) and 1L == 1L) {
                    emit(id, prefixCost + COMPLETION_COST + EXTRA_LETTER_COST * (len - n), false)
                    found++
                    continue
                }
                val w = d.wordAt(id)
                if (w >= 0) push(d.frequency(w), true, w, len)
                var k = d.child(id)
                while (k >= 0) {
                    push(d.maxFrequency(k), false, k, len + 1)
                    k = d.sibling(k)
                }
            }
        }

        private fun same(a: Char, b: Char) = a == b || Chars.base(a) == Chars.base(b)

        private fun subCost(tc: Char, dc: Char): Float {
            if (tc == dc) return 0f
            if (tc.lowercaseChar() == dc.lowercaseChar()) return CASE
            val a = Chars.base(tc)
            val b = Chars.base(dc)
            if (a == b) return CHEAP
            return if (keys != null && keys.near(a, b)) NEAR else 1f
        }

        private fun insCost(dc: Char) = if (dc == '\'' || dc == '’' || dc == '-') CHEAP else 1f

        /** Dropping the typed letter at [j]: cheap when it doubles the one before it. */
        private fun delCost(j: Int): Float {
            val tc = t[j - 1]
            if (j >= 2 && same(tc, t[j - 2])) return DOUBLE
            return if (tc == '\'' || tc == '’') 0.3f else 1f
        }

        private fun digraph(a: Char, b: Char, dc: Char): Boolean {
            val x = a.lowercaseChar()
            val y = b.lowercaseChar()
            return when (dc.lowercaseChar()) {
                'ä' -> x == 'a' && y == 'e'
                'ö' -> x == 'o' && y == 'e'
                'ü' -> x == 'u' && y == 'e'
                'ß' -> x == 's' && y == 's'
                else -> false
            }
        }
    }

    companion object {
        /** Points of frequency (0 to 255) one edit costs. */
        const val EDIT_WEIGHT = 75f

        /** Autocorrect replaces a word only when the best correction scores at least this. */
        const val AUTOCORRECT_MARGIN = 60f

        const val CURRENT_LANGUAGE_BONUS = 0.5f
        const val CASE = 0.02f
        const val CHEAP = 0.1f
        const val NEAR = 0.6f
        const val TRANSPOSE = 0.5f

        /** A letter typed once that the word has twice, or the other way round. */
        const val DOUBLE = 0.35f

        /** Corrections costing this or less are cheap-only matches and get [CHEAP_TIER_BONUS]. */
        const val CHEAP_TIER = 0.4f
        const val CHEAP_TIER_BONUS = 100f

        /** A typed prefix this close to a word's start counts for completions. */
        const val COMPLETE_MAX = 0.35f
        const val COMPLETION_COST = 0.35f
        const val EXTRA_LETTER_COST = 0.04f
        const val COMPLETIONS = 6
        const val MAX_TYPED = 40
    }
}
