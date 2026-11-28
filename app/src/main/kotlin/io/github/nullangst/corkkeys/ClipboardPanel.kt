/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The clipboard history panel. It takes the keys' place, like the editing panel. Tap a clip to
 * type it; long-press it for Pin and Delete. Clear removes every unpinned clip.
 */
@SuppressLint("ViewConstructor")
class ClipboardPanel(ctx: Context, private val host: Host) : LinearLayout(ctx) {
    interface Host {
        fun clipPicked(text: String)
        fun clipPin(c: Clip, on: Boolean)
        fun clipDelete(c: Clip)
        fun clipClear()
        fun clipClose()
        fun buttonFeedback(v: View)
    }

    private val d = resources.displayMetrics.density
    private val list = LinearLayout(ctx).apply { orientation = VERTICAL }
    private val scroll = ScrollView(ctx).apply { addView(list) }
    private val title = TextView(ctx)
    private val close = TextView(ctx)
    private val clear = TextView(ctx)
    private var pal: Palette = Themes.light
    private var clips: List<Clip> = emptyList()
    private var open: Clip? = null

    init {
        orientation = VERTICAL
        val bar = LinearLayout(ctx).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun button(tv: TextView, text: Int, onClick: (View) -> Unit) = tv.apply {
            this.text = context.getString(text)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                host.buttonFeedback(it)
                onClick(it)
            }
        }
        button(close, R.string.edit_close) { host.clipClose() }
        button(clear, R.string.clip_clear) { host.clipClear() }
        title.apply {
            text = context.getString(R.string.clip_title)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        val h = (40 * d).toInt()
        bar.addView(close, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h))
        bar.addView(title, LayoutParams(0, h, 1f))
        bar.addView(clear, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h))
        addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun applyPalette(p: Palette) {
        pal = p
        setBackgroundColor(p.keyboardBg)
        title.setTextColor(p.textDim)
        close.setTextColor(p.text)
        clear.setTextColor(p.accent)
        show(clips)
    }

    fun show(items: List<Clip>) {
        clips = items
        if (open != null && open !in items) open = null
        list.removeAllViews()
        clear.visibility = if (items.any { !it.pinned }) VISIBLE else INVISIBLE
        if (items.isEmpty()) {
            list.addView(TextView(context).apply {
                text = context.getString(R.string.clip_empty)
                textSize = 14f
                setTextColor(pal.textDim)
                setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
            })
            return
        }
        for (c in items) {
            list.addView(card(c), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                val m = (4 * d).toInt()
                setMargins(m * 2, m, m * 2, m)
            })
            if (c === open) list.addView(actions(c))
        }
    }

    private fun card(c: Clip) = TextView(context).apply {
        text = c.text
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        textSize = 15f
        setTextColor(pal.text)
        typeface = if (c.pinned) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val p = (12 * d).toInt()
        setPadding(p, p, p, p)
        background = GradientDrawable().apply {
            cornerRadius = 8 * d
            setColor(pal.key)
            if (c.pinned) setStroke((2 * d).toInt(), pal.accent)
        }
        contentDescription = (if (c.pinned) context.getString(R.string.clip_pinned) + ": " else "") + c.text
        isClickable = true
        isFocusable = true
        setOnClickListener {
            host.buttonFeedback(it)
            host.clipPicked(c.text)
        }
        setOnLongClickListener {
            open = if (open === c) null else c
            show(clips)
            true
        }
    }

    private fun actions(c: Clip) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.END
        fun act(text: Int, onClick: () -> Unit) = TextView(context).apply {
            this.text = context.getString(text)
            textSize = 14f
            setTextColor(pal.accent)
            val p = (12 * d).toInt()
            setPadding(p, p / 2, p, p / 2)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                host.buttonFeedback(it)
                open = null
                onClick()
            }
        }
        addView(act(if (c.pinned) R.string.clip_unpin else R.string.clip_pin) { host.clipPin(c, !c.pinned) })
        addView(act(R.string.clip_delete) { host.clipDelete(c) })
    }
}
