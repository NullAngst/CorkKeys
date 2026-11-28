/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.InputConnection

/**
 * The Paste button, and the only file allowed to touch the clipboard (CI checks that).
 *
 * A press first asks the app to paste, the same as the app's own Paste menu item, so the
 * keyboard never sees the text. Only when the app says it can't (terminals, mostly) does this
 * read the clipboard, once, on that press, and type what is there. Nothing is kept, nothing
 * listens for changes, and nothing checks whether anything is on the clipboard before a press.
 */
object Paste {
    fun paste(ctx: Context, ic: InputConnection?) {
        if (ic == null) return
        if (ic.performContextMenuAction(android.R.id.paste)) return
        try {
            val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(ctx)
            if (!text.isNullOrEmpty()) ic.commitText(text, 1)
        } catch (_: Exception) {
        }
    }
}
