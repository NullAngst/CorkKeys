/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.inputmethod.InputMethodManager

/**
 * Voice typing by handing over to another keyboard. Settings lists your other enabled
 * keyboards; once you pick one, a mic button in the tools row switches to it. CorkKeys has no
 * voice code of its own, and there is no mic button until a keyboard is picked.
 */
object VoiceHandoff {
    class Choice(val id: String, val label: String)

    /** Every enabled keyboard except this one. Android only switches between enabled keyboards. */
    fun keyboards(ctx: Context): List<Choice> = try {
        val imm = ctx.getSystemService(InputMethodManager::class.java)
        imm?.enabledInputMethodList.orEmpty()
            .filter { it.packageName != ctx.packageName }
            .map { Choice(it.id, it.loadLabel(ctx.packageManager).toString()) }
            .sortedBy { it.label.lowercase() }
    } catch (_: Exception) {
        emptyList()
    }

    /** The picked keyboard, if one is picked and still enabled. */
    fun picked(ctx: Context, id: String): Choice? =
        if (id.isEmpty()) null else keyboards(ctx).firstOrNull { it.id == id }

    fun switchTo(service: InputMethodService, id: String): Boolean = try {
        service.switchInputMethod(id)
        true
    } catch (_: Exception) {
        false
    }
}
