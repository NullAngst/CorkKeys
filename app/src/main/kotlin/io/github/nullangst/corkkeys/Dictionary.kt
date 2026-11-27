/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.content.Context
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

/**
 * One language's word list in memory: every word with its frequency (0 to 255) and whether the
 * list marks it possibly offensive, plus a trie over them for lookups and corrections.
 *
 * The trie is a few primitive arrays with no object per node. Words are kept in one char
 * buffer in the list's order, most common first, so a word's id is also its rank.
 */
class Dictionary private constructor(
    val language: String,
    private val text: CharArray,
    private val start: IntArray,
    private val freqs: ByteArray,
    private val flags: ByteArray,
    private val nodeChar: CharArray,
    private val firstChild: IntArray,
    private val nextSibling: IntArray,
    private val nodeWord: IntArray,
    private val nodeMax: ByteArray,
) {
    val size: Int get() = freqs.size
    val nodes: Int get() = nodeChar.size

    fun word(id: Int): String = String(text, start[id], start[id + 1] - start[id])
    fun frequency(id: Int): Int = freqs[id].toInt() and 0xFF
    fun offensive(id: Int): Boolean = flags[id].toInt() and FLAG_OFFENSIVE != 0

    // The trie, for Corrector and SwipeDecoder. Node 0 is the root and holds no letter.
    fun child(node: Int): Int = firstChild[node]
    fun sibling(node: Int): Int = nextSibling[node]
    fun char(node: Int): Char = nodeChar[node]
    fun wordAt(node: Int): Int = nodeWord[node]

    /** The highest frequency of any word at or below [node]. */
    fun maxFrequency(node: Int): Int = nodeMax[node].toInt() and 0xFF

    /** The word's id, or -1. Exact, capitals and accents included. */
    fun find(word: CharSequence): Int {
        var node = 0
        for (c in word) {
            node = childWith(node, c)
            if (node < 0) return -1
        }
        return nodeWord[node]
    }

    private fun childWith(node: Int, c: Char): Int {
        var n = firstChild[node]
        while (n >= 0) {
            if (nodeChar[n] == c) return n
            n = nextSibling[n]
        }
        return -1
    }

    /** Rough bytes held, for maintenance.md. */
    fun bytes(): Long = text.size * 2L + start.size * 4L + freqs.size * 2L +
        nodeChar.size * 2L + firstChild.size * 4L + nextSibling.size * 4L + nodeWord.size * 4L + nodeMax.size

    private class Builder(val language: String) {
        var text = CharArray(1 shl 20)
        var textLen = 0
        var start = IntArray(1 shl 16)
        var freqs = ByteArray(1 shl 16)
        var flags = ByteArray(1 shl 16)
        var words = 0

        var nodeChar = CharArray(1 shl 18)
        var firstChild = IntArray(1 shl 18)
        var nextSibling = IntArray(1 shl 18)
        var nodeWord = IntArray(1 shl 18)
        var parent = IntArray(1 shl 18)
        var nodes = 0

        init {
            newNode('\u0000', -1)
        }

        private fun newNode(c: Char, par: Int): Int {
            if (nodes == nodeChar.size) {
                val n = nodes * 2
                nodeChar = nodeChar.copyOf(n)
                firstChild = firstChild.copyOf(n)
                nextSibling = nextSibling.copyOf(n)
                nodeWord = nodeWord.copyOf(n)
                parent = parent.copyOf(n)
            }
            val id = nodes++
            nodeChar[id] = c
            firstChild[id] = -1
            nodeWord[id] = -1
            parent[id] = par
            if (par >= 0) {
                nextSibling[id] = firstChild[par]
                firstChild[par] = id
            } else {
                nextSibling[id] = -1
            }
            return id
        }

        private fun nodeFor(word: String): Int {
            var node = 0
            for (c in word) {
                var n = firstChild[node]
                while (n >= 0 && nodeChar[n] != c) n = nextSibling[n]
                node = if (n >= 0) n else newNode(c, node)
            }
            return node
        }

        fun add(word: String, freq: Int, offensive: Boolean) {
            if (word.isEmpty()) return
            val node = nodeFor(word)
            val existing = nodeWord[node]
            if (existing >= 0) {
                // Already listed: an extra list can only add the offensive mark.
                if (offensive) flags[existing] = (flags[existing].toInt() or FLAG_OFFENSIVE).toByte()
                return
            }
            if (words + 2 > start.size) {
                val n = start.size * 2
                start = start.copyOf(n)
                freqs = freqs.copyOf(n)
                flags = flags.copyOf(n)
            }
            while (textLen + word.length > text.size) text = text.copyOf(text.size * 2)
            start[words] = textLen
            word.toCharArray(text, textLen)
            textLen += word.length
            freqs[words] = freq.coerceIn(0, 255).toByte()
            flags[words] = (if (offensive) FLAG_OFFENSIVE else 0).toByte()
            nodeWord[node] = words
            words++
            start[words] = textLen
        }

        fun build(): Dictionary {
            val max = ByteArray(nodes)
            // Children are always made after their parent, so going backwards visits every child first.
            for (n in nodes - 1 downTo 0) {
                val w = nodeWord[n]
                var m = max[n].toInt() and 0xFF
                if (w >= 0) m = maxOf(m, freqs[w].toInt() and 0xFF)
                max[n] = m.toByte()
                val p = parent[n]
                if (p >= 0 && m > (max[p].toInt() and 0xFF)) max[p] = m.toByte()
            }
            return Dictionary(
                language,
                text.copyOf(textLen), start.copyOf(words + 1), freqs.copyOf(words), flags.copyOf(words),
                nodeChar.copyOf(nodes), firstChild.copyOf(nodes), nextSibling.copyOf(nodes), nodeWord.copyOf(nodes), max,
            )
        }
    }

    companion object {
        const val FLAG_OFFENSIVE = 1

        /**
         * Builds from lines of "word<TAB>frequency<TAB>flag". [extra] lines are merged in after
         * the main list. Blank and malformed lines are skipped.
         */
        fun build(language: String, lines: Sequence<String>, extra: Sequence<String> = emptySequence()): Dictionary {
            val b = Builder(language)
            for (seq in listOf(lines, extra)) for (line in seq) {
                if (line.isEmpty() || line[0] == '#') continue
                val t1 = line.indexOf('\t')
                if (t1 <= 0) continue
                val t2 = line.indexOf('\t', t1 + 1)
                val f = (if (t2 < 0) line.substring(t1 + 1) else line.substring(t1 + 1, t2)).trim().toIntOrNull() ?: continue
                val flag = if (t2 < 0) "" else line.substring(t2 + 1).trim()
                b.add(line.substring(0, t1), f, flag == "o")
            }
            return b.build()
        }

        fun read(language: String, list: InputStream, extra: InputStream?): Dictionary {
            val main = BufferedReader(InputStreamReader(GZIPInputStream(list, 1 shl 16), Charsets.UTF_8), 1 shl 16)
            val more = extra?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)) }
            main.use { m ->
                val extraLines = more?.use { it.readLines() }.orEmpty()
                return build(language, m.lineSequence(), extraLines.asSequence())
            }
        }

        /** A small list of the user's own learned words, which count as real words. */
        fun ofWords(language: String, words: Collection<String>, freq: Int): Dictionary =
            build(language, words.asSequence().map { "$it\t$freq\t-" })
    }
}

/**
 * The loaded word lists, shared by the keyboard and the spell checker. Nothing loads until
 * something asks, and a list loads on one background thread: until it's ready, typing works
 * and the strip stays empty. Only languages that are asked for get loaded.
 */
object Dictionaries {
    private val loaded = ConcurrentHashMap<String, Dictionary>()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "corkkeys-words").apply { priority = Thread.MIN_PRIORITY + 1 } }

    fun get(language: String): Dictionary? = loaded[language]

    /** Starts loading [languages] that aren't loaded yet; [ready] runs on the worker thread after each. */
    fun load(ctx: Context, languages: Collection<String>, ready: () -> Unit) {
        val app = ctx.applicationContext ?: ctx
        for (lang in languages) {
            if (loaded.containsKey(lang) || !loading.add(lang)) continue
            worker.execute {
                try {
                    val list = app.assets.open("words/$lang.txt.gz")
                    val extra = try {
                        app.assets.open("words/${lang}_extra.txt")
                    } catch (_: Exception) {
                        null
                    }
                    loaded[lang] = Dictionary.read(lang, list, extra)
                } catch (_: Exception) {
                } finally {
                    loading.remove(lang)
                }
                ready()
            }
        }
    }

    /** Lets go of every list but [keep], so turning suggestions off frees the memory. */
    fun keepOnly(keep: Collection<String>) {
        for (k in loaded.keys) if (k !in keep) loaded.remove(k)
    }

    /** Runs [block] on the word thread, after any loading already queued. */
    fun onWorker(block: () -> Unit) = worker.execute(block)
}
