/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The editing panel: arrows, select, select all, cut, copy, paste, start and end of line. It
 * takes the letters' place until you close it. With Select on, the arrows and start/end extend
 * the selection, like holding shift on a hardware keyboard. Arrows and backspace repeat while
 * held.
 */
@SuppressLint("ViewConstructor")
class EditingPanel(ctx: Context, private val host: Host) : LinearLayout(ctx) {
    enum class Action { LEFT, RIGHT, UP, DOWN, HOME, END, SELECT, SELECT_ALL, CUT, COPY, PASTE, BACKSPACE, CLOSE }

    interface Host {
        /** [select]: whether Select is on. */
        fun edit(a: Action, select: Boolean, v: View)
    }

    private val d = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val cells = ArrayList<Pair<TextView, Action>>()
    private var select = false
    private var pal: Palette = Themes.light

    init {
        orientation = VERTICAL
        val grid = listOf(
            listOf(Action.HOME, Action.UP, Action.END, Action.SELECT_ALL),
            listOf(Action.LEFT, Action.SELECT, Action.RIGHT, Action.CUT),
            listOf(Action.BACKSPACE, Action.DOWN, null, Action.COPY),
            listOf(Action.CLOSE, null, null, Action.PASTE),
        )
        for (row in grid) {
            val line = LinearLayout(ctx).apply { orientation = HORIZONTAL }
            for (a in row) {
                if (a == null) {
                    line.addView(View(ctx), LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                    continue
                }
                val tv = cell(a)
                cells += tv to a
                line.addView(tv, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    val m = (3 * d).toInt()
                    setMargins(m, m, m, m)
                })
            }
            addView(line, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setPadding(0, (3 * d).toInt(), 0, (3 * d).toInt())
    }

    private fun label(a: Action): String = when (a) {
        Action.LEFT -> "←"
        Action.RIGHT -> "→"
        Action.UP -> "↑"
        Action.DOWN -> "↓"
        Action.HOME -> context.getString(R.string.edit_home)
        Action.END -> context.getString(R.string.edit_end)
        Action.SELECT -> context.getString(R.string.edit_select)
        Action.SELECT_ALL -> context.getString(R.string.edit_select_all)
        Action.CUT -> context.getString(R.string.edit_cut)
        Action.COPY -> context.getString(R.string.edit_copy)
        Action.PASTE -> context.getString(R.string.edit_paste)
        Action.BACKSPACE -> "⌫"
        Action.CLOSE -> context.getString(R.string.edit_close)
    }

    private fun description(a: Action): String = context.getString(
        when (a) {
            Action.LEFT -> R.string.cd_left
            Action.RIGHT -> R.string.cd_right
            Action.UP -> R.string.cd_up
            Action.DOWN -> R.string.cd_down
            Action.BACKSPACE -> R.string.cd_backspace
            Action.HOME -> R.string.edit_home
            Action.END -> R.string.edit_end
            Action.SELECT -> R.string.edit_select
            Action.SELECT_ALL -> R.string.edit_select_all
            Action.CUT -> R.string.edit_cut
            Action.COPY -> R.string.edit_copy
            Action.PASTE -> R.string.edit_paste
            Action.CLOSE -> R.string.edit_close
        },
    )

    private fun repeats(a: Action) = a == Action.LEFT || a == Action.RIGHT || a == Action.UP || a == Action.DOWN || a == Action.BACKSPACE

    @SuppressLint("ClickableViewAccessibility")
    private fun cell(a: Action) = TextView(context).apply {
        text = label(a)
        contentDescription = description(a)
        gravity = Gravity.CENTER
        textSize = if (label(a).length == 1) 22f else 14f
        isClickable = true
        isFocusable = true
        if (repeats(a)) {
            // Fires on press, then again and again while held.
            var held: Runnable? = null
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        fire(a, v)
                        var delay = REPEAT_START_MS
                        val r = object : Runnable {
                            override fun run() {
                                fire(a, v)
                                delay = REPEAT_MS
                                handler.postDelayed(this, delay)
                            }
                        }
                        held = r
                        handler.postDelayed(r, delay)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        held?.let { handler.removeCallbacks(it) }
                        held = null
                    }
                }
                true
            }
            // TalkBack double-tap.
            setOnClickListener { fire(a, it) }
        } else {
            setOnClickListener { fire(a, it) }
        }
    }

    private fun fire(a: Action, v: View) {
        if (a == Action.SELECT) {
            select = !select
            restyle()
        }
        host.edit(a, select, v)
        if (a == Action.CLOSE || a == Action.CUT || a == Action.COPY || a == Action.SELECT_ALL || a == Action.PASTE) {
            if (a != Action.SELECT_ALL && select) {
                select = false
                restyle()
            }
        }
    }

    /** Opening the panel always starts with Select off. */
    fun reset() {
        select = false
        handler.removeCallbacksAndMessages(null)
        restyle()
    }

    fun applyPalette(p: Palette) {
        pal = p
        setBackgroundColor(p.keyboardBg)
        restyle()
    }

    private fun restyle() {
        for ((tv, a) in cells) {
            val on = a == Action.SELECT && select
            tv.background = GradientDrawable().apply {
                cornerRadius = 6 * d
                setColor(if (on) pal.accent else if (a == Action.CLOSE) pal.keyFunction else pal.key)
            }
            tv.setTextColor(if (on) pal.onAccent else pal.text)
            if (a == Action.SELECT) tv.isSelected = on
        }
    }

    companion object {
        const val REPEAT_START_MS = 400L
        const val REPEAT_MS = 60L
    }
}
