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
 * backspace, cursor moves and backspace by word, and with suggestions on, the word being typed
 * (the app's composing region), autocorrect and its undo.
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

    /** The word being typed, underlined in the app. Empty when there is none. */
    val word = StringBuilder()

    /** Every letter of [word] came from a key tap: what learning counts as "typed in full". */
    private var tapped = true

    /** Caps lock was on when the word started, so suggestions come in capitals too. */
    private var capsLock = false

    private class Correction(val typed: String, val replacement: String, val separator: String)

    /** The last autocorrect, while backspace can still undo it. */
    private var lastCorrection: Correction? = null

    /** A word whose autocorrect was undone; it isn't corrected again. */
    private var rejected: String? = null

    private var lastEditAt = 0L

    fun start(initialSelStart: Int, initialSelEnd: Int) {
        selStart = initialSelStart
        selEnd = initialSelEnd
        lastSpace = -1
        autoSpaced = false
        slideBefore = null
        word.setLength(0)
        tapped = true
        lastCorrection = null
        rejected = null
        resync()
    }

    /** The field is closing: whatever is being typed stays as it is. */
    fun finish() {
        if (word.isNotEmpty()) {
            ic?.finishComposingText()
            word.setLength(0)
        }
    }

    /**
     * The app moved the cursor or changed the selection, whoever did it. Updates that follow
     * our own edits are ours; anything else is the user moving the cursor, which ends the word
     * being typed and starts over from what the app now has around the cursor.
     */
    fun onSelection(newStart: Int, newEnd: Int, candStart: Int, candEnd: Int) {
        val moved = newStart != selStart || newEnd != selEnd
        selStart = newStart
        selEnd = newEnd
        if (!moved || slideBefore != null) return
        if (SystemClock.uptimeMillis() - lastEditAt < OWN_EDIT_MS) return
        autoSpaced = false
        lastSpace = -1
        lastCorrection = null
        rejected = null
        if (word.isNotEmpty()) {
            val atEnd = newStart == newEnd && candStart >= 0 && candEnd == newEnd && candEnd - candStart == word.length
            if (atEnd) return
            ic?.finishComposingText()
            word.setLength(0)
        }
        resync()
        svc.wordChanged()
    }

    private fun edit(block: (InputConnection) -> Unit) {
        val c = ic ?: return
        lastEditAt = SystemClock.uptimeMillis()
        block(c)
    }

    private fun isWordStart(c: Char) = c.isLetter()
    private fun isWordChar(c: Char) = c.isLetter() || c == '\'' || c == '’'

    /**
     * With suggestions on and the cursor just after a word (not in the middle of one), that word
     * becomes the word being typed again, so its suggestions show. Read fresh from the app.
     */
    private fun resync() {
        if (!svc.composing() || word.isNotEmpty()) return
        val c = ic ?: return
        if (selStart < 0 || selStart != selEnd) return
        val after = c.getTextAfterCursor(1, 0)
        if (!after.isNullOrEmpty() && isWordChar(after[0])) return
        val before = c.getTextBeforeCursor(RESYNC_CHARS, 0) ?: return
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        while (i < before.length && !isWordStart(before[i])) i++
        val w = before.substring(i)
        if (w.isEmpty() || w.length > RESYNC_CHARS - 1) return
        edit { it.setComposingRegion(selEnd - w.length, selEnd) }
        word.append(w)
        tapped = false
        capsLock = false
    }

    /**
     * Ends the word being typed. With [autocorrect], the corrector may swap it first; returns the
     * replacement when it did. A word that goes in exactly as tapped counts toward learning.
     */
    private fun finishWord(autocorrect: Boolean): String? {
        if (word.isEmpty()) return null
        val typed = word.toString()
        var out = typed
        if (autocorrect && typed != rejected && svc.autocorrectOn()) {
            svc.autocorrection(typed)?.let { out = Chars.applyCase(typed, it, capsLock) }
        }
        edit { c ->
            if (out != typed) c.commitText(out, 1) else c.finishComposingText()
        }
        if (out == typed && tapped) svc.learn(typed)
        word.setLength(0)
        tapped = true
        svc.wordChanged()
        return if (out != typed) out else null
    }

    /** Picks a word from the strip in place of the word being typed. [slot] 0 is the word as typed. */
    fun pick(choice: String, slot: Int) {
        val typed = word.toString()
        val out = if (typed.isEmpty()) choice else Chars.applyCase(typed, choice, capsLock)
        val learn = slot == 0 && tapped && typed.isNotEmpty()
        lastCorrection = null
        rejected = null
        lastSpace = -1
        word.setLength(0)
        tapped = true
        edit { c ->
            c.beginBatchEdit()
            c.commitText(out, 1)
            val after = c.getTextAfterCursor(1, 0)
            autoSpaced = Punctuation.spaceAfterPick(after)
            if (autoSpaced) c.commitText(" ", 1)
            c.endBatchEdit()
        }
        if (learn) svc.learn(typed)
        svc.wordChanged()
    }

    /** A key's text, or a pick from a long-press popup. */
    fun text(s: String, shiftLocked: Boolean = false) {
        if (s == "\n") {
            enter()
            return
        }
        val one = if (s.length == 1) s[0] else '\u0000'
        if (svc.composing() && (isWordStart(one) || word.isNotEmpty() && isWordChar(one))) {
            if (word.isEmpty()) capsLock = shiftLocked
            lastCorrection = null
            autoSpaced = false
            lastSpace = -1
            word.append(s)
            edit { it.setComposingText(word, 1) }
            svc.wordChanged()
            return
        }
        // Punctuation ends the word, and may autocorrect it first.
        val typed = word.toString()
        val corrected = finishWord(Punctuation.pullsBack(one) || one == '"')
        val pull = autoSpaced && s.length == 1 && Punctuation.pullsBack(one)
        autoSpaced = false
        lastSpace = -1
        lastCorrection = null
        edit { c ->
            if (pull) {
                val b = c.getTextBeforeCursor(1, 0)
                if (b != null && b.length == 1 && b[0] == ' ') c.deleteSurroundingText(1, 0)
            }
            c.commitText(s, 1)
        }
        if (corrected != null) lastCorrection = Correction(typed, corrected, s)
    }

    fun space(now: Long) {
        // A space the keyboard already added counts as this tap, so a second tap can still make ". ".
        if (autoSpaced) {
            autoSpaced = false
            lastSpace = now
            return
        }
        val typed = word.toString()
        val corrected = finishWord(true)
        lastCorrection = null
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
        if (corrected != null) lastCorrection = Correction(typed, corrected, " ")
    }

    fun enter() {
        finishWord(false)
        autoSpaced = false
        lastSpace = -1
        lastCorrection = null
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
        // Straight after an autocorrect: put the typed word back, and leave it alone from now on.
        lastCorrection?.let { lc ->
            lastCorrection = null
            val n = lc.replacement.length + lc.separator.length
            if (c.getTextBeforeCursor(n, 0)?.toString() == lc.replacement + lc.separator) {
                edit {
                    it.beginBatchEdit()
                    it.deleteSurroundingText(n, 0)
                    it.commitText(lc.typed, 1)
                    it.endBatchEdit()
                }
                rejected = lc.typed
                return
            }
        }
        if (word.isNotEmpty()) {
            val cut = if (word.length >= 2 && Character.isSurrogatePair(word[word.length - 2], word[word.length - 1])) 2 else 1
            word.setLength(word.length - cut)
            edit { it.setComposingText(word, 1) }
            if (word.isEmpty()) edit { it.finishComposingText() }
            svc.wordChanged()
            return
        }
        if (svc.field.raw) {
            key(KeyEvent.KEYCODE_DEL)
            return
        }
        val sel = c.getSelectedText(0)
        if (!sel.isNullOrEmpty()) {
            edit { it.commitText("", 1) }
            return
        }
        val before = c.getTextBeforeCursor(2, 0)
        if (before.isNullOrEmpty()) {
            key(KeyEvent.KEYCODE_DEL)
            return
        }
        val n = if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
        edit { it.deleteSurroundingTextInCodePoints(1, 0) }
        // Backspacing into a word brings its suggestions back. The cursor moved left by what went.
        if (selStart >= n && selStart == selEnd) {
            selStart -= n
            selEnd -= n
            resync()
            if (word.isNotEmpty()) svc.wordChanged()
        }
    }

    /** Moves the cursor [steps] characters, negative for left. Arrow key events work in every app, terminals too. */
    fun cursor(steps: Int) {
        finishWord(false)
        lastCorrection = null
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { key(code) }
    }

    /** One key press and release. [shift] holds shift, so arrows extend the selection. */
    fun key(code: Int, shift: Boolean = false) {
        val c = ic ?: return
        val now = SystemClock.uptimeMillis()
        val meta = if (shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        lastEditAt = now
        c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }

    /** Backspace by word: selects the last [words] words so you see what will go. */
    fun selectWords(words: Int) {
        val c = ic ?: return
        if (slideBefore == null) {
            finishWord(false)
            lastCorrection = null
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

        /** Cursor updates this soon after an edit of ours are taken as caused by it. */
        const val OWN_EDIT_MS = 500L

        /** The longest word picked back up from before the cursor. */
        const val RESYNC_CHARS = 48
    }
}
