/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.content.Context
import android.os.UserManager
import java.io.File

/** Where a list of words lives. A file in credential-encrypted storage on the phone, a list in the tests. */
interface WordStore {
    /** False until the storage can be read: before the first unlock after a reboot. */
    val ready: Boolean get() = true
    fun read(): List<String>
    fun write(words: Collection<String>)
}

/**
 * One word per line in the app's normal (credential-encrypted) storage, so it can't be read
 * before the phone has been unlocked once after a reboot. Until then nothing is read or written.
 */
class FileWordStore(ctx: Context, private val name: String) : WordStore {
    private val app = ctx.applicationContext ?: ctx

    override val ready: Boolean get() = unlocked()

    private fun unlocked(): Boolean = try {
        app.getSystemService(UserManager::class.java)?.isUserUnlocked != false
    } catch (_: Exception) {
        false
    }

    override fun read(): List<String> {
        if (!unlocked()) return emptyList()
        return try {
            File(app.filesDir, name).takeIf { it.exists() }?.readLines(Charsets.UTF_8)?.filter { it.isNotBlank() }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun write(words: Collection<String>) {
        if (!unlocked()) return
        try {
            val f = File(app.filesDir, name)
            if (words.isEmpty()) {
                f.delete()
                return
            }
            val tmp = File(app.filesDir, "$name.tmp")
            tmp.writeText(words.sorted().joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        } catch (_: Exception) {
        }
    }
}

/**
 * Learned words: a word missing from the lists is kept once it has been typed in full twice,
 * and only while the Privacy page switch is on. Only the word itself is stored. The count of
 * how often a word has been typed stays in memory and is gone when the keyboard closes.
 */
class LearnedWords(private val store: WordStore) {
    private var words: MutableSet<String>? = null
    private val pending = HashMap<String, Int>()

    /** Bumped on every change, so the corrector knows to rebuild. */
    var version = 0
        private set

    /** Read once the storage is ready; before that, an empty set that isn't kept. */
    fun all(): Set<String> = words ?: store.read().toMutableSet().also { if (store.ready) words = it }

    /**
     * A word went into the app as typed, letter by letter. [allowed] is the switch, incognito
     * and the field together; [known] whether the word is in a list already.
     */
    fun typedInFull(word: String, allowed: Boolean, known: Boolean): Boolean {
        if (!allowed) {
            pending.clear()
            return false
        }
        if (known || word.length < 2 || word.length > MAX_LENGTH || !word.all { it.isLetter() || it == '\'' || it == '’' || it == '-' }) return false
        val set = all() as MutableSet<String>
        if (word in set) return false
        val n = (pending[word] ?: 0) + 1
        if (n < TIMES) {
            pending[word] = n
            return false
        }
        pending.remove(word)
        set += word
        store.write(set)
        version++
        return true
    }

    fun remove(word: String) {
        val set = all() as MutableSet<String>
        if (set.remove(word)) {
            store.write(set)
            version++
        }
    }

    /** Switching learning off forgets what was on its way to being learned. */
    fun forgetPending() = pending.clear()

    fun clear() {
        words = mutableSetOf()
        pending.clear()
        store.write(emptyList())
        version++
    }

    companion object {
        const val TIMES = 2
        const val MAX_LENGTH = 40
        const val FILE = "learned_words.txt"

        @Volatile
        private var shared: LearnedWords? = null

        /** One copy for the keyboard and the settings screens, which run in the same process. */
        fun get(ctx: Context): LearnedWords = shared ?: synchronized(this) {
            shared ?: LearnedWords(FileWordStore(ctx, FILE)).also { shared = it }
        }
    }
}
