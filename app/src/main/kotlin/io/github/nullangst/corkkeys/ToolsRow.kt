/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * The row the tools button opens in the strip: editing panel, one-handed, floating, the voice
 * keyboard (only once one is picked in settings) and settings.
 */
@SuppressLint("ViewConstructor")
class ToolsRow(ctx: Context, private val host: Host) : LinearLayout(ctx) {
    enum class Tool(val icon: Int, val desc: Int) {
        EDIT(R.drawable.ic_edit, R.string.cd_edit),
        SETTINGS(R.drawable.ic_settings, R.string.cd_settings),
    }

    interface Host {
        fun tool(t: Tool, v: View)
    }

    private val buttons = LinkedHashMap<Tool, ImageView>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        for (t in Tool.entries) {
            val b = ImageView(ctx).apply {
                setImageResource(t.icon)
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = ctx.getString(t.desc)
                isClickable = true
                isFocusable = true
                setOnClickListener { host.tool(t, it) }
            }
            buttons[t] = b
            addView(b, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    fun applyPalette(p: Palette) {
        for (b in buttons.values) b.imageTintList = ColorStateList.valueOf(p.text)
    }

    fun setShown(t: Tool, shown: Boolean) {
        buttons[t]?.visibility = if (shown) VISIBLE else GONE
    }
}
