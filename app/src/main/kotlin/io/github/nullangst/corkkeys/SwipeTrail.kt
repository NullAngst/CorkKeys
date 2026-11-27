/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock

/**
 * The trail that follows the finger while swiping, in the theme's accent colour. The newest
 * part is thickest; older points thin out and fade, and the whole trail fades away after the
 * finger lifts.
 */
class SwipeTrail(private val view: KeyboardView) {
    private var xs = FloatArray(256)
    private var ys = FloatArray(256)
    private var ts = LongArray(256)
    private var n = 0
    private var liftedAt = -1L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun add(x: Float, y: Float) {
        if (liftedAt >= 0) {
            n = 0
            liftedAt = -1
        }
        if (n == xs.size) {
            xs = xs.copyOf(n * 2)
            ys = ys.copyOf(n * 2)
            ts = ts.copyOf(n * 2)
        }
        xs[n] = x
        ys[n] = y
        ts[n] = SystemClock.uptimeMillis()
        n++
        view.invalidate()
    }

    fun lift() {
        liftedAt = SystemClock.uptimeMillis()
        view.invalidate()
    }

    fun clear() {
        n = 0
        liftedAt = -1
        view.invalidate()
    }

    fun draw(c: Canvas) {
        if (n < 2) return
        val now = SystemClock.uptimeMillis()
        val fade = if (liftedAt < 0) 1f else 1f - (now - liftedAt) / FADE_MS.toFloat()
        // Fully faded, or a swipe that never lifted (cancelled) has gone stale: stop redrawing.
        if (fade <= 0f || liftedAt < 0 && now - ts[n - 1] > TAIL_MS) {
            n = 0
            return
        }
        val d = view.resources.displayMetrics.density
        val color = view.pal.accent and 0x00FFFFFF
        for (i in 1 until n) {
            val age = (now - ts[i]).coerceAtLeast(0)
            val life = 1f - age / TAIL_MS.toFloat()
            if (life <= 0f) continue
            val a = (life * fade * 230).toInt().coerceIn(0, 255)
            paint.color = color or (a shl 24)
            paint.strokeWidth = d * (2f + 5f * life)
            c.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], paint)
        }
        // Keep drawing while the tail or the fade is still moving.
        view.postInvalidateOnAnimation()
    }

    companion object {
        const val TAIL_MS = 600L
        const val FADE_MS = 250L
    }
}
