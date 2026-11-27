/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.content.Context

/**
 * Words you removed from suggestions with a long-press. Kept the same way as learned words,
 * only the word, so they stay removed. They are never suggested again, but they still count as
 * real words for autocorrect.
 */
class RemovedWords(private val store: WordStore) {
    private var words: MutableSet<String>? = null

    var version = 0
        private set

    fun all(): Set<String> = words ?: store.read().map { it.lowercase() }.toMutableSet().also { if (store.ready) words = it }

    fun add(word: String) {
        val set = all() as MutableSet<String>
        if (set.add(word.lowercase())) {
            store.write(set)
            version++
        }
    }

    fun remove(word: String) {
        val set = all() as MutableSet<String>
        if (set.remove(word.lowercase())) {
            store.write(set)
            version++
        }
    }

    fun clear() {
        words = mutableSetOf()
        store.write(emptyList())
        version++
    }

    companion object {
        const val FILE = "removed_words.txt"

        @Volatile
        private var shared: RemovedWords? = null

        fun get(ctx: Context): RemovedWords = shared ?: synchronized(this) {
            shared ?: RemovedWords(FileWordStore(ctx, FILE)).also { shared = it }
        }
    }
}
