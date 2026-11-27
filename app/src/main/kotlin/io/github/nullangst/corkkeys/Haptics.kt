/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Key-press vibration through the view's own haptic feedback. That needs no VIBRATE permission,
 * and it follows Android's own keyboard vibration setting, so switching that off silences this
 * too. No key sounds.
 */
object Haptics {
    fun key(v: View, enabled: Boolean) {
        if (enabled) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** A firmer tick for long-press popups and menus opening. */
    fun longPress(v: View, enabled: Boolean) {
        if (enabled) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
