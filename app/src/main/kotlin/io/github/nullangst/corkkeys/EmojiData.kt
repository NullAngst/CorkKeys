/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.content.Context
import android.graphics.Paint

/** One emoji: what it types, its Unicode name (read by TalkBack), and its skin tone variants. */
class EmojiEntry(val emoji: String, val name: String, val tones: List<String>)

class EmojiGroup(val id: String, val entries: List<EmojiEntry>)

/**
 * The emoji list from assets/emoji.txt, made from Unicode's emoji-test.txt by
 * tools/convert_emoji.sh. Emoji the phone's font can't draw are left out, so a phone on an older
 * Android never shows empty boxes.
 */
object EmojiData {
    /** The tab icon for each group. */
    val icons = linkedMapOf(
        "smileys" to "😀", "people" to "👋", "animals" to "🐶", "food" to "🍔", "travel" to "🚗",
        "activities" to "⚽", "objects" to "💡", "symbols" to "🔣", "flags" to "🏁",
    )

    fun parse(lines: Sequence<String>): List<EmojiGroup> {
        val out = ArrayList<EmojiGroup>()
        var id: String? = null
        var entries = ArrayList<EmojiEntry>()
        fun close() {
            val g = id ?: return
            if (entries.isNotEmpty()) out += EmojiGroup(g, entries)
        }
        for (line in lines) {
            if (line.startsWith("% group ")) {
                close()
                id = line.substring(8).trim()
                entries = ArrayList()
                continue
            }
            if (line.isEmpty() || line.startsWith("%") || id == null) continue
            val parts = line.split('\t')
            if (parts.size < 2 || parts[0].isEmpty()) continue
            val tones = parts.getOrNull(2)?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
            entries += EmojiEntry(parts[0], parts[1], tones)
        }
        close()
        return out
    }

    @Volatile
    private var cached: List<EmojiGroup>? = null

    /** Loads once, dropping what the font can't draw. Slow the first time: call it off the main thread. */
    fun load(ctx: Context): List<EmojiGroup> = cached ?: synchronized(this) {
        cached ?: try {
            val paint = Paint()
            val all = (ctx.applicationContext ?: ctx).assets.open("emoji.txt").bufferedReader(Charsets.UTF_8).use { parse(it.lineSequence()) }
            all.map { g ->
                EmojiGroup(
                    g.id,
                    g.entries.filter { paint.hasGlyph(it.emoji) }.map { e ->
                        EmojiEntry(e.emoji, e.name, e.tones.filter { paint.hasGlyph(it) })
                    },
                )
            }.filter { it.entries.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }.also { cached = it }
    }
}

/**
 * Recently used emoji and the skin tone picked for each one. Kept in memory only, so they're
 * gone when the keyboard restarts: nothing about what you type is written down for them.
 */
class EmojiRecents {
    private val recent = ArrayList<String>()
    private val tone = HashMap<String, String>()

    val list: List<String> get() = recent

    fun used(emoji: String) {
        recent.remove(emoji)
        recent.add(0, emoji)
        while (recent.size > MAX) recent.removeAt(recent.size - 1)
    }

    /** Remembers the tone picked for [base], so its cell shows that tone from now on. */
    fun pickTone(base: String, variant: String) {
        if (variant == base) tone.remove(base) else tone[base] = variant
    }

    fun toned(base: String): String = tone[base] ?: base

    companion object {
        const val MAX = 32
    }
}
