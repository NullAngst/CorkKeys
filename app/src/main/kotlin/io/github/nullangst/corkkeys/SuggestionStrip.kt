/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The strip above the keys: the tools button at the left end, three suggestion slots in the
 * middle (what you typed, the best guess, the next guess), and the Paste button at the right
 * end, always there. With tools open, the slots give way to the tools row.
 */
@SuppressLint("ViewConstructor")
class SuggestionStrip(ctx: Context, private val host: Host) : LinearLayout(ctx) {
    interface Host {
        fun toolsPressed()
        fun pastePressed()
        fun picked(slot: Int, word: String)
        fun removeAsked(word: String)
        fun buttonFeedback(v: View)
    }

    private val d = resources.displayMetrics.density
    private val toolsButton = icon(R.drawable.ic_tools, R.string.cd_tools) {
        host.buttonFeedback(it)
        host.toolsPressed()
    }
    private val pasteButton = icon(R.drawable.ic_paste, R.string.cd_paste) {
        host.buttonFeedback(it)
        host.pastePressed()
    }
    private val middle = FrameLayout(ctx)
    private val slots = LinearLayout(ctx).apply { orientation = HORIZONTAL }
    private val slotViews = ArrayList<TextView>()
    private val dividers = ArrayList<View>()
    private var words: List<String> = emptyList()
    var toolsRow: View? = null
        set(v) {
            field?.let { middle.removeView(it) }
            field = v
            if (v != null) {
                v.visibility = GONE
                middle.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }

    var toolsOpen = false
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        for (i in 0 until SLOTS) {
            if (i > 0) {
                val div = View(ctx)
                dividers += div
                slots.addView(div, LayoutParams((d).toInt().coerceAtLeast(1), (HEIGHT_DP * 0.45f * d).toInt()).apply { gravity = Gravity.CENTER_VERTICAL })
            }
            val tv = TextView(ctx).apply {
                gravity = Gravity.CENTER
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                textSize = 16f
                setPadding((4 * d).toInt(), 0, (4 * d).toInt(), 0)
                val slot = i
                setOnClickListener { v ->
                    words.getOrNull(slot)?.let {
                        host.buttonFeedback(v)
                        host.picked(slot, it)
                    }
                }
                setOnLongClickListener {
                    val w = words.getOrNull(slot)
                    if (slot > 0 && w != null) {
                        host.removeAsked(w)
                        true
                    } else {
                        false
                    }
                }
            }
            slotViews += tv
            slots.addView(tv, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        middle.addView(slots, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val side = (BUTTON_DP * d).toInt()
        addView(toolsButton, LayoutParams(side, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(middle, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(pasteButton, LayoutParams(side, ViewGroup.LayoutParams.MATCH_PARENT))
        show(emptyList(), -1)
    }

    private fun icon(res: Int, desc: Int, onClick: (View) -> Unit) = ImageView(context).apply {
        setImageResource(res)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(desc)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick(it) }
    }

    private var pal: Palette = Themes.light

    fun applyPalette(p: Palette) {
        pal = p
        setBackgroundColor(p.keyboardBg)
        val tint = ColorStateList.valueOf(p.textDim)
        toolsButton.imageTintList = if (toolsOpen) ColorStateList.valueOf(p.accent) else tint
        pasteButton.imageTintList = tint
        for (div in dividers) div.setBackgroundColor(p.line)
        for (tv in slotViews) tv.setTextColor(p.text)
    }

    /** Fills the slots. [highlight] is the slot autocorrect will use, drawn bold. */
    fun show(list: List<String>, highlight: Int) {
        words = list
        for (i in 0 until SLOTS) {
            val tv = slotViews[i]
            val w = list.getOrNull(i)
            tv.text = w ?: ""
            tv.contentDescription = w
            tv.isClickable = w != null
            tv.typeface = if (i == highlight) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tv.importantForAccessibility = if (w == null) IMPORTANT_FOR_ACCESSIBILITY_NO else IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        for (div in dividers) div.visibility = if (list.isEmpty()) INVISIBLE else VISIBLE
    }

    fun setToolsOpen(open: Boolean) {
        toolsOpen = open
        slots.visibility = if (open) GONE else VISIBLE
        toolsRow?.visibility = if (open) VISIBLE else GONE
        toolsButton.setImageResource(if (open) R.drawable.ic_close else R.drawable.ic_tools)
        toolsButton.contentDescription = context.getString(if (open) R.string.cd_tools_close else R.string.cd_tools)
        applyPalette(pal)
    }

    companion object {
        const val SLOTS = 3
        const val HEIGHT_DP = 44f
        const val BUTTON_DP = 48f
    }
}
