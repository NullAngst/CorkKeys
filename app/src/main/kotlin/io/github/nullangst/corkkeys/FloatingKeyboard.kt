/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * The floating keyboard: a smaller keyboard you drag anywhere by its handle. The app underneath
 * stays full size and usable around it. Android's own keyboard APIs allow this: the keyboard's
 * window covers the screen, but only the keyboard's own rectangle takes touches (see
 * KeyboardService.onComputeInsets), and the app is told the keyboard takes no space, so it
 * doesn't shrink.
 *
 * The position is kept as a fraction of the free space, so it survives rotation.
 */
@SuppressLint("ViewConstructor")
class FloatingKeyboard(ctx: Context, private val host: Host) : LinearLayout(ctx) {
    interface Host {
        fun floatMoved(fx: Float, fy: Float)
        fun dockPressed()
    }

    private val d = resources.displayMetrics.density
    private val grip = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_grip)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = ctx.getString(R.string.cd_grip)
    }
    private val dock = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_dock)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = ctx.getString(R.string.cd_dock)
        isClickable = true
        isFocusable = true
        setOnClickListener { host.dockPressed() }
    }

    /** The view being moved: the whole keyboard, strip included. */
    var target: View? = null

    /** Where the keyboard may go: the window minus the system bars and cutouts. */
    val safe = Rect()

    private var downX = 0f
    private var downY = 0f
    private var startLeft = 0
    private var startTop = 0

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val side = (HANDLE_DP * d).toInt()
        addView(View(ctx), LayoutParams(side, side))
        addView(grip, LayoutParams(0, side, 1f))
        addView(dock, LayoutParams(side, side))
        grip.setOnTouchListener { _, e -> drag(e) }
    }

    fun applyPalette(p: Palette) {
        background = GradientDrawable().apply {
            setColor(p.keyboardBg)
            cornerRadii = floatArrayOf(12 * d, 12 * d, 12 * d, 12 * d, 0f, 0f, 0f, 0f)
        }
        grip.imageTintList = ColorStateList.valueOf(p.textDim)
        dock.imageTintList = ColorStateList.valueOf(p.text)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun drag(e: MotionEvent): Boolean {
        val t = target ?: return false
        val lp = t.layoutParams as? FrameLayout.LayoutParams ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                startLeft = lp.leftMargin
                startTop = lp.topMargin
            }
            MotionEvent.ACTION_MOVE -> {
                move(t, startLeft + (e.rawX - downX).toInt(), startTop + (e.rawY - downY).toInt())
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val (fx, fy) = fractions(t)
                host.floatMoved(fx, fy)
            }
        }
        return true
    }

    private fun move(t: View, left: Int, top: Int) {
        val lp = t.layoutParams as FrameLayout.LayoutParams
        val w = t.width
        val h = t.height
        lp.gravity = Gravity.TOP or Gravity.START
        lp.leftMargin = left.coerceIn(safe.left, maxOf(safe.left, safe.right - w))
        lp.topMargin = top.coerceIn(safe.top, maxOf(safe.top, safe.bottom - h))
        t.layoutParams = lp
    }

    private fun fractions(t: View): Pair<Float, Float> {
        val lp = t.layoutParams as FrameLayout.LayoutParams
        val freeX = (safe.width() - t.width).coerceAtLeast(1)
        val freeY = (safe.height() - t.height).coerceAtLeast(1)
        return ((lp.leftMargin - safe.left).toFloat() / freeX).coerceIn(0f, 1f) to
            ((lp.topMargin - safe.top).toFloat() / freeY).coerceIn(0f, 1f)
    }

    /** Puts the keyboard at a saved position, once it has been measured. */
    fun place(fx: Float, fy: Float) {
        val t = target ?: return
        if (t.width == 0 || t.height == 0) {
            t.post { if (t.width > 0) place(fx, fy) }
            return
        }
        val freeX = (safe.width() - t.width).coerceAtLeast(0)
        val freeY = (safe.height() - t.height).coerceAtLeast(0)
        move(t, safe.left + (fx * freeX).toInt(), safe.top + (fy * freeY).toInt())
    }

    companion object {
        const val HANDLE_DP = 28f

        fun dockedParams(): FrameLayout.LayoutParams =
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
    }
}
