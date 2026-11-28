/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * Everything that changes the app's text goes through here: typed characters, space, enter,
 * backspace, cursor moves and backspace by word.
 *
 * The keyboard never trusts its own copy of the text. Whatever it needs to know (the character
 * before the cursor, where the cursor is) it reads from the app at the moment it needs it, and
 * the cursor position is taken from the app's own updates every time it moves.
 */
class Composer(private val svc: KeyboardService) {
    private val ic: InputConnection? get() = svc.currentInputConnection

    /** Where the app says the cursor is. -1 when it hasn't said. */
    var selStart = -1
        private set
    var selEnd = -1
        private set

    private var lastSpace = -1L

    /** The keyboard put a space after a word on its own: a picked suggestion or a swiped word. */
    var autoSpaced = false

    private var slideBefore: CharSequence? = null
    private var slideEnd = -1
    private var slideWords = 0

    fun start(initialSelStart: Int, initialSelEnd: Int) {
        selStart = initialSelStart
        selEnd = initialSelEnd
        lastSpace = -1
        autoSpaced = false
        slideBefore = null
    }

    /** The app moved the cursor or changed the selection, whoever did it. */
    fun onSelection(newStart: Int, newEnd: Int) {
        val moved = newStart != selStart || newEnd != selEnd
        selStart = newStart
        selEnd = newEnd
        if (moved && slideBefore == null) {
            // Only our own edits keep these going; any other move ends them.
            if (!ownEdit) {
                autoSpaced = false
                lastSpace = -1
            }
        }
        ownEdit = false
    }

    /** Set just before an edit of ours, so the cursor update it causes isn't read as the user moving it. */
    private var ownEdit = false

    private fun edit(block: (InputConnection) -> Unit) {
        val c = ic ?: return
        ownEdit = true
        block(c)
    }

    /** A key's text, or a pick from a long-press popup. */
    fun text(s: String) {
        if (s == "\n") {
            enter()
            return
        }
        val pull = autoSpaced && s.length == 1 && Punctuation.pullsBack(s[0])
        autoSpaced = false
        lastSpace = -1
        edit { c ->
            if (pull) {
                val b = c.getTextBeforeCursor(1, 0)
                if (b != null && b.length == 1 && b[0] == ' ') c.deleteSurroundingText(1, 0)
            }
            c.commitText(s, 1)
        }
    }

    fun space(now: Long) {
        // A space the keyboard already added counts as this tap, so a second tap can still make ". ".
        if (autoSpaced) {
            autoSpaced = false
            lastSpace = now
            return
        }
        edit { c ->
            val before = c.getTextBeforeCursor(2, 0)
            val since = if (lastSpace < 0) -1 else now - lastSpace
            if (Punctuation.doubleSpace(before, since, svc.field, svc.prefs.doubleSpace)) {
                c.beginBatchEdit()
                c.deleteSurroundingText(1, 0)
                c.commitText(". ", 1)
                c.endBatchEdit()
                lastSpace = -1
            } else {
                c.commitText(" ", 1)
                lastSpace = now
            }
        }
    }

    fun enter() {
        autoSpaced = false
        lastSpace = -1
        val f = svc.field
        if (f.enter != Enter.NEWLINE) {
            edit { it.performEditorAction(f.action) }
            return
        }
        key(KeyEvent.KEYCODE_ENTER)
    }

    fun backspace() {
        autoSpaced = false
        lastSpace = -1
        val c = ic ?: return
        if (svc.field.raw) {
            key(KeyEvent.KEYCODE_DEL)
            return
        }
        val sel = c.getSelectedText(0)
        if (!sel.isNullOrEmpty()) {
            edit { it.commitText("", 1) }
            return
        }
        val before = c.getTextBeforeCursor(1, 0)
        if (before.isNullOrEmpty()) key(KeyEvent.KEYCODE_DEL) else edit { it.deleteSurroundingTextInCodePoints(1, 0) }
    }

    /** Moves the cursor [steps] characters, negative for left. Arrow key events work in every app, terminals too. */
    fun cursor(steps: Int) {
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { key(code) }
    }

    /** One key press and release. [shift] holds shift, so arrows extend the selection. */
    fun key(code: Int, shift: Boolean = false) {
        val c = ic ?: return
        val now = SystemClock.uptimeMillis()
        val meta = if (shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        ownEdit = true
        c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }

    /** Backspace by word: selects the last [words] words so you see what will go. */
    fun selectWords(words: Int) {
        val c = ic ?: return
        if (slideBefore == null) {
            slideBefore = c.getTextBeforeCursor(SLIDE_CHARS, 0) ?: ""
            slideEnd = if (selStart == selEnd) selEnd else -1
        }
        slideWords = words
        if (slideEnd < 0) return
        val n = BackspaceSlide.charsFor(slideBefore ?: "", words)
        edit { it.setSelection(slideEnd - n, slideEnd) }
    }

    /** The finger lifted: delete what the slide selected, or put the cursor back if nothing is. */
    fun deleteSelectedWords() {
        val before = slideBefore ?: return
        val n = BackspaceSlide.charsFor(before, slideWords)
        val end = slideEnd
        slideBefore = null
        slideWords = 0
        autoSpaced = false
        lastSpace = -1
        edit { c ->
            when {
                n > 0 && end >= 0 -> {
                    c.setSelection(end - n, end)
                    c.commitText("", 1)
                }
                n > 0 -> c.deleteSurroundingText(n, 0)
                end >= 0 -> c.setSelection(end, end)
            }
        }
    }

    companion object {
        /** How far back backspace by word looks. */
        const val SLIDE_CHARS = 2000
    }
}
