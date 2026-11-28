/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.text.InputType
import android.view.inputmethod.EditorInfo

enum class FieldKind { TEXT, EMAIL, URL, NUMBER, DATETIME, PHONE }

enum class Enter { NEWLINE, GO, SEARCH, SEND, NEXT, DONE, PREVIOUS }

/**
 * What the app said about the field being typed in, read once when the field opens. Every
 * "not in password fields" rule in the keyboard asks this class.
 */
class FieldInfo(
    val kind: FieldKind,
    val password: Boolean,
    /** The app asked for no suggestions (TYPE_TEXT_FLAG_NO_SUGGESTIONS). */
    val noSuggestions: Boolean,
    /** A private tab or incognito app (IME_FLAG_NO_PERSONALIZED_LEARNING). */
    val noLearning: Boolean,
    val enter: Enter,
    /** The editor action to send for [enter], from IME_MASK_ACTION. */
    val action: Int,
    val inputType: Int,
    /** TYPE_NULL: a terminal or a game that wants plain key events, no composing. */
    val raw: Boolean,
) {
    private val text: Boolean get() = kind == FieldKind.TEXT && !password && !raw

    /** Suggestions at all. [inNoSuggestFields] is the setting that shows them anyway. */
    fun allowsSuggestions(inNoSuggestFields: Boolean): Boolean =
        !password && !raw && (kind == FieldKind.TEXT || kind == FieldKind.EMAIL || kind == FieldKind.URL) &&
            (!noSuggestions || inNoSuggestFields)

    /** Replacing what was typed only makes sense in plain text: never in addresses or passwords. */
    val allowsAutocorrect: Boolean get() = text
    val allowsSwipe: Boolean get() = text
    val allowsLearning: Boolean get() = !password && !raw && !noLearning
    val allowsBubbles: Boolean get() = !password
    val allowsDoubleSpace: Boolean get() = text
    val allowsAutoCaps: Boolean get() = text

    /** Incognito here: switched on by hand ([manual]), or asked for by the app. */
    fun incognito(manual: Boolean): Boolean = manual || noLearning

    companion object {
        val PLAIN = FieldInfo(FieldKind.TEXT, false, false, false, Enter.NEWLINE, EditorInfo.IME_ACTION_NONE, InputType.TYPE_CLASS_TEXT, false)
    }
}

object Field {
    /** The layout a field opens with: the pads for numbers, dates, times and phone numbers, letters for the rest. */
    fun layoutFor(kind: FieldKind, language: String, numberRow: Boolean): Layout = when (kind) {
        FieldKind.NUMBER -> LayoutNumber.build(false)
        FieldKind.DATETIME -> LayoutNumber.build(true)
        FieldKind.PHONE -> LayoutPhone.build()
        FieldKind.TEXT, FieldKind.EMAIL, FieldKind.URL -> Letters.build(Letters.data(language), numberRow)
    }

    fun read(info: EditorInfo?): FieldInfo = if (info == null) FieldInfo.PLAIN else read(info.inputType, info.imeOptions)

    fun read(inputType: Int, imeOptions: Int): FieldInfo {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val raw = inputType == InputType.TYPE_NULL
        val password = when (cls) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
        val kind = when (cls) {
            InputType.TYPE_CLASS_NUMBER -> FieldKind.NUMBER
            InputType.TYPE_CLASS_PHONE -> FieldKind.PHONE
            InputType.TYPE_CLASS_DATETIME -> FieldKind.DATETIME
            InputType.TYPE_CLASS_TEXT -> when (variation) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldKind.EMAIL
                InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
                else -> FieldKind.TEXT
            }
            else -> FieldKind.TEXT
        }
        val noSuggestions = cls == InputType.TYPE_CLASS_TEXT && inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        val noLearning = imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val enter = if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) Enter.NEWLINE else when (action) {
            EditorInfo.IME_ACTION_GO -> Enter.GO
            EditorInfo.IME_ACTION_SEARCH -> Enter.SEARCH
            EditorInfo.IME_ACTION_SEND -> Enter.SEND
            EditorInfo.IME_ACTION_NEXT -> Enter.NEXT
            EditorInfo.IME_ACTION_DONE -> Enter.DONE
            EditorInfo.IME_ACTION_PREVIOUS -> Enter.PREVIOUS
            else -> Enter.NEWLINE
        }
        return FieldInfo(kind, password, noSuggestions, noLearning, enter, action, inputType, raw)
    }
}
