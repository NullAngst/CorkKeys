/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The long-press space menu: the languages that are on, the current one marked, and the
 * incognito switch. Covers the keys with a scrim; a tap outside the card closes it.
 */
@SuppressLint("ViewConstructor")
class LanguageMenu(ctx: Context, private val host: Host) : FrameLayout(ctx) {
    interface Host {
        fun languagePicked(lang: String)
        fun incognitoToggled(on: Boolean)
        fun menuClosed()
    }

    private val d = resources.displayMetrics.density
    private val card = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    init {
        isClickable = true
        addView(card, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) host.menuClosed()
        return true
    }

    fun fill(pal: Palette, languages: List<String>, current: String, incognito: Boolean) {
        card.removeAllViews()
        setBackgroundColor((pal.keyboardBg and 0x00FFFFFF) or 0xB0000000.toInt())
        card.background = GradientDrawable().apply {
            cornerRadius = 14 * d
            setColor(pal.surface)
        }
        val pad = (6 * d).toInt()
        card.setPadding(pad, pad, pad, pad)
        for (lang in languages) {
            val on = lang == current
            card.addView(row(pal, (if (on) "✓  " else "    ") + Languages.name(context, lang), on) { host.languagePicked(lang) })
        }
        val label = context.getString(if (incognito) R.string.menu_incognito_on else R.string.menu_incognito_off)
        card.addView(row(pal, label, incognito) { host.incognitoToggled(!incognito) })
    }

    private fun row(pal: Palette, text: String, on: Boolean, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        textSize = 17f
        setTextColor(if (on) pal.accent else pal.text)
        minWidth = (220 * d).toInt()
        minHeight = (48 * d).toInt()
        gravity = Gravity.CENTER_VERTICAL
        setPadding((16 * d).toInt(), 0, (16 * d).toInt(), 0)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
}
