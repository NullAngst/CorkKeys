/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.content.ComponentName
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import java.util.Locale

/**
 * Which languages are on and which one is in use. Each language is also one of Android's input
 * subtypes (res/xml/method.xml), and switching here switches the subtype too, so apps that ask
 * Android which language the keyboard is typing in get the right answer.
 */
object Languages {
    val ALL = listOf("en", "de", "es")

    fun short(lang: String): String = lang.uppercase(Locale.ROOT)

    fun locale(lang: String): Locale = when (lang) {
        "de" -> Locale.GERMAN
        "es" -> Locale.forLanguageTag("es")
        else -> Locale.US
    }

    fun name(ctx: Context, lang: String): String = ctx.getString(
        when (lang) {
            "de" -> R.string.lang_de
            "es" -> R.string.lang_es
            else -> R.string.lang_en
        },
    )

    /** Languages whose words come up together: every one that is on, the picked one first. */
    fun mixed(current: String, on: List<String>, mixing: Boolean): List<String> =
        if (mixing) listOf(current) + on.filter { it != current } else listOf(current)

    /** The space bar's text: "EN" on its own, "EN • DE" with two languages at once. */
    fun spaceLabel(current: String, on: List<String>, mixing: Boolean): String =
        mixed(current, on, mixing).joinToString(" • ") { short(it) }

    /** The language of one of our subtypes, or null for something else. */
    fun of(subtype: InputMethodSubtype?): String? {
        val tag = subtype?.languageTag?.takeIf { it.isNotEmpty() } ?: return null
        val lang = tag.substringBefore('-').lowercase(Locale.ROOT)
        return lang.takeIf { it in ALL }
    }

    /** Tells Android the keyboard now types in [lang]. Fails quietly: it only matters to apps that ask. */
    fun announce(service: InputMethodService, lang: String) {
        try {
            val imm = service.getSystemService(InputMethodManager::class.java) ?: return
            val id = ComponentName(service, KeyboardService::class.java).flattenToShortString()
            val info = imm.inputMethodList.firstOrNull { it.id == id } ?: return
            if (of(imm.currentInputMethodSubtype) == lang) return
            for (i in 0 until info.subtypeCount) {
                val st = info.getSubtypeAt(i)
                if (of(st) == lang) {
                    service.switchInputMethod(id, st)
                    return
                }
            }
        } catch (_: Exception) {
        }
    }
}
