/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import java.util.Base64

/** One copied piece of text. [time] is wall-clock milliseconds, so expiry survives a restart. */
class Clip(val text: String, val time: Long, var pinned: Boolean = false)

/**
 * The history itself, newest first. Every copy is kept in memory for an hour, or until pinned;
 * pinned clips stay until deleted. [toSave] picks what goes to encrypted storage: every pinned
 * clip and the newest [keep] unpinned ones. Pure, so it is tested off the phone.
 */
class ClipList(var keep: Int) {
    val items = ArrayList<Clip>()

    /** Adds a copy. The same text copied again moves to the top instead of appearing twice. */
    fun add(text: String, now: Long): Boolean {
        if (text.isBlank()) return false
        val old = items.firstOrNull { it.text == text }
        if (old != null) items.remove(old)
        items.add(0, Clip(text, now, old?.pinned == true))
        // A hard cap on memory, oldest unpinned first.
        while (items.count { !it.pinned } > MAX_IN_MEMORY) {
            items.removeAt(items.indexOfLast { !it.pinned })
        }
        return true
    }

    /** Drops unpinned clips older than [EXPIRY_MS]. Returns true when anything went. */
    fun expire(now: Long): Boolean = items.removeAll { !it.pinned && now - it.time > EXPIRY_MS }

    /** What the panel shows: pinned clips first, then the rest, newest first in each. */
    fun shown(now: Long): List<Clip> {
        expire(now)
        return items.filter { it.pinned } + items.filter { !it.pinned }
    }

    fun pin(clip: Clip, on: Boolean) {
        clip.pinned = on
    }

    fun delete(clip: Clip) {
        items.remove(clip)
    }

    /** Clear clears the unpinned clips; pinned ones go one at a time, on purpose. */
    fun clearUnpinned() {
        items.removeAll { !it.pinned }
    }

    fun toSave(): List<Clip> = items.filter { it.pinned } + items.filter { !it.pinned }.take(keep)

    companion object {
        /** Gboard's default: unpinned clips go after an hour. */
        const val EXPIRY_MS = 60L * 60L * 1000L
        const val MAX_IN_MEMORY = 200

        /** One clip per line: pinned flag, time, then the text in base64 so newlines survive. */
        fun encode(clips: List<Clip>): String = clips.joinToString("\n") {
            (if (it.pinned) "p" else "-") + "\t" + it.time + "\t" +
                Base64.getEncoder().encodeToString(it.text.toByteArray(Charsets.UTF_8))
        }

        fun decode(s: String): List<Clip> = s.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@mapNotNull null
            val time = parts[1].toLongOrNull() ?: return@mapNotNull null
            val text = try {
                String(Base64.getDecoder().decode(parts[2]), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                return@mapNotNull null
            }
            Clip(text, time, parts[0] == "p")
        }.toList()
    }
}

/**
 * Clipboard history: off until switched on on the Privacy page. While on, every copy anywhere
 * is kept in memory for an hour, and the newest few (as many as the setting says) plus every
 * pinned clip are saved encrypted, so they survive a restart. Copies an app marks sensitive
 * (password managers do) are never recorded, and neither is anything copied while incognito.
 *
 * Android lets only the active keyboard read the clipboard while another app is in front, which
 * is what makes this possible. This file and Paste.kt are the only ones allowed to touch the
 * clipboard (CI checks that).
 */
class ClipboardHistory private constructor(ctx: Context) {
    private val app = ctx.applicationContext ?: ctx
    private val prefs = Prefs.open(app)
    private val store = ClipStore(app, FILE)
    val list = ClipList(prefs.clipKeep)
    private var loaded = false
    private var listening: ClipboardManager? = null

    /** Asked on every copy: is the keyboard incognito right now? */
    var incognito: () -> Boolean = { prefs.incognito }

    /** Called when the list changes, so an open panel can redraw. */
    var changed: (() -> Unit)? = null

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { onCopy() }

    /** Starts or stops listening to match the switch. */
    fun sync() {
        if (prefs.clipHistory) start() else stop()
    }

    private fun start() {
        if (listening != null) return
        val cm = app.getSystemService(ClipboardManager::class.java) ?: return
        cm.addPrimaryClipChangedListener(listener)
        listening = cm
        load()
    }

    private fun stop() {
        listening?.removePrimaryClipChangedListener(listener)
        listening = null
    }

    private fun onCopy() {
        if (!prefs.clipHistory || incognito()) return
        try {
            val cm = listening ?: return
            val clip = cm.primaryClip ?: return
            if (sensitive(clip.description)) return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(app)?.toString() ?: return
            load()
            if (list.add(text, System.currentTimeMillis())) {
                save()
                changed?.invoke()
            }
        } catch (_: Exception) {
        }
    }

    /** Password managers and others mark secrets with EXTRA_IS_SENSITIVE (read by name, so it works before Android 13 too). */
    private fun sensitive(d: ClipDescription?): Boolean =
        d?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true

    /** Reads the saved clips once the phone has been unlocked; until then, memory only. */
    fun load() {
        if (loaded) return
        // Null: locked, or the key can't be used yet. Try again later rather than overwrite the file.
        val saved = store.read() ?: return
        loaded = true
        val now = System.currentTimeMillis()
        for (c in ClipList.decode(saved).asReversed()) {
            if (list.items.none { it.text == c.text }) list.items.add(0, c)
        }
        list.items.sortByDescending { it.time }
        if (list.expire(now)) save()
    }

    fun save() {
        // Until the saved clips have been read, writing would throw them away.
        if (!loaded) load()
        if (!loaded) return
        list.keep = prefs.clipKeep
        val keep = list.toSave()
        if (keep.isEmpty()) store.delete() else store.write(ClipList.encode(keep))
    }

    fun shown(): List<Clip> {
        load()
        val before = list.items.size
        val out = list.shown(System.currentTimeMillis())
        if (out.size != before) save()
        return out
    }

    fun pin(c: Clip, on: Boolean) {
        list.pin(c, on)
        save()
    }

    fun delete(c: Clip) {
        list.delete(c)
        save()
    }

    fun clearUnpinned() {
        list.clearUnpinned()
        save()
    }

    /** Switching history off forgets everything: memory, the saved file, pins included. */
    fun wipe() {
        stop()
        list.items.clear()
        store.delete()
    }

    companion object {
        const val FILE = "clips.bin"

        @Volatile
        private var shared: ClipboardHistory? = null

        fun get(ctx: Context): ClipboardHistory = shared ?: synchronized(this) {
            shared ?: ClipboardHistory(ctx).also { shared = it }
        }
    }
}
