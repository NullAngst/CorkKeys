/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import kotlin.math.hypot

/** What a touch on the keys turns into. KeyboardService does the typing. */
interface TouchHost {
    /** A finger went down on a key, or slid onto another one. */
    fun pressed(k: PlacedKey, slid: Boolean)

    /** The bubble and pressed look go away. */
    fun released()

    fun tap(k: PlacedKey)
    fun popupPicked(text: String)

    /** Long-press on a key that has no popup: space opens the language menu, comma opens settings. */
    fun longPress(k: PlacedKey): Boolean
    fun backspaceRepeat()
    fun cursor(steps: Int)
    fun selectWords(words: Int)
    fun deleteSelectedWords()

    fun swipeAllowed(): Boolean
    fun swipeMoved(x: Float, y: Float)
    fun swiped(xs: FloatArray, ys: FloatArray)
}

/**
 * Pointer tracking: tap, long-press, backspace repeat, multi-touch, and the start of a swipe,
 * a space bar slide or a backspace slide.
 *
 * A key types when its finger lifts. When a second finger lands while the first is still down,
 * the first key types right away, so fast two-thumb typing comes out in order. Shift acts on the
 * press, so holding it while tapping a letter gives a capital.
 */
class Touch(private val view: KeyboardView, private val host: TouchHost) {
    private enum class Mode { PRESS, POPUP, SWIPE, SPACE, BACKSPACE, REPEAT, DONE }

    private class Pointer(val id: Int, var key: PlacedKey, val x0: Float, val y0: Float) {
        var mode = Mode.PRESS
        var xs = FloatArray(64)
        var ys = FloatArray(64)
        var n = 0
        var space: SpaceCursor? = null
        var back: BackspaceSlide? = null
        var repeats = 0

        fun add(x: Float, y: Float) {
            if (n == xs.size) {
                xs = xs.copyOf(n * 2)
                ys = ys.copyOf(n * 2)
            }
            xs[n] = x
            ys[n] = y
            n++
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val pointers = LinkedHashMap<Int, Pointer>()
    private var longPressFor: Pointer? = null

    private val longPress = Runnable {
        val p = longPressFor ?: return@Runnable
        longPressFor = null
        if (p.mode != Mode.PRESS || pointers[p.id] !== p) return@Runnable
        val k = p.key
        if (k.key.kind == Kind.BACKSPACE) {
            p.mode = Mode.REPEAT
            repeat(p)
            return@Runnable
        }
        if (k.key.popup.isNotEmpty() && k.key.kind == Kind.CHAR && !k.key.longPressSettings) {
            host.released()
            view.openPopup(k)
            p.mode = Mode.POPUP
            return@Runnable
        }
        if (host.longPress(k)) {
            host.released()
            p.mode = Mode.DONE
        }
    }

    private var repeating: Pointer? = null
    private val repeatTick = Runnable { repeating?.let { repeat(it) } }

    private fun repeat(p: Pointer) {
        if (p.mode != Mode.REPEAT || pointers[p.id] !== p) return
        host.backspaceRepeat()
        p.repeats++
        repeating = p
        handler.postDelayed(repeatTick, if (p.repeats < REPEAT_FAST_AFTER) REPEAT_MS else REPEAT_FAST_MS)
    }

    fun cancelAll() {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(repeatTick)
        longPressFor = null
        repeating = null
        pointers.clear()
        view.closePopup()
        host.released()
    }

    fun onTouchEvent(e: MotionEvent): Boolean {
        val plan = view.plan ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                val k = plan.find(x, y) ?: return true
                // Rolling typing: a key still held types now, before the new one.
                for (other in pointers.values) {
                    if (other.mode == Mode.PRESS && other.key.key.kind == Kind.CHAR) {
                        host.tap(other.key)
                        other.mode = Mode.DONE
                    }
                }
                val p = Pointer(e.getPointerId(i), k, x, y)
                p.add(x, y)
                pointers[p.id] = p
                host.pressed(k, false)
                when (k.key.kind) {
                    Kind.SHIFT -> {
                        host.tap(k)
                        p.mode = Mode.DONE
                    }
                    Kind.SPACE -> p.space = SpaceCursor(x, view.dp(CURSOR_STEP_DP))
                    Kind.BACKSPACE -> p.back = BackspaceSlide(x, plan.unit * 0.8f)
                    else -> {}
                }
                scheduleLongPress(p)
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val p = pointers[e.getPointerId(i)] ?: continue
                    move(p, e.getX(i), e.getY(i), plan)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                val p = pointers.remove(e.getPointerId(i)) ?: return true
                move(p, e.getX(i), e.getY(i), plan)
                up(p)
            }

            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun scheduleLongPress(p: Pointer) {
        handler.removeCallbacks(longPress)
        longPressFor = p
        val delay = if (p.key.key.kind == Kind.BACKSPACE) REPEAT_START_MS else LONG_PRESS_MS
        handler.postDelayed(longPress, delay)
    }

    private fun move(p: Pointer, x: Float, y: Float, plan: Plan) {
        when (p.mode) {
            Mode.POPUP -> if (view.popupSelect(x, y)) view.invalidateOverlay()
            Mode.SWIPE -> {
                p.add(x, y)
                host.swipeMoved(x, y)
            }
            Mode.SPACE -> {
                val n = p.space?.move(x) ?: 0
                if (n != 0) host.cursor(n)
            }
            Mode.BACKSPACE -> if (p.back?.move(x) == true) host.selectWords(p.back?.words ?: 0)
            Mode.PRESS -> press(p, x, y, plan)
            Mode.REPEAT, Mode.DONE -> {}
        }
    }

    private fun press(p: Pointer, x: Float, y: Float, plan: Plan) {
        p.add(x, y)
        val k = p.key
        when (k.key.kind) {
            Kind.SPACE -> {
                val n = p.space?.move(x) ?: 0
                if (p.space?.active == true) {
                    cancelLongPress(p)
                    host.released()
                    p.mode = Mode.SPACE
                    if (n != 0) host.cursor(n)
                }
                return
            }
            Kind.BACKSPACE -> {
                if (p.back?.move(x) == true || p.back?.active == true) {
                    cancelLongPress(p)
                    host.released()
                    p.mode = Mode.BACKSPACE
                    host.selectWords(p.back?.words ?: 0)
                }
                return
            }
            else -> {}
        }
        // A swipe starts once the finger leaves its first key by more than half a key width.
        if (k.key.isLetter && pointers.size == 1 && host.swipeAllowed() && k.box.distance(x, y) > plan.unit / 2f) {
            cancelLongPress(p)
            host.released()
            p.mode = Mode.SWIPE
            for (j in 0 until p.n) host.swipeMoved(p.xs[j], p.ys[j])
            return
        }
        // Without swipe, sliding onto another key moves the press there.
        if (!k.box.contains(x, y) && hypot(x - p.x0, y - p.y0) > plan.unit * 0.3f) {
            val now = plan.find(x, y) ?: return
            if (now !== k && now.key.kind == Kind.CHAR && k.key.kind == Kind.CHAR) {
                p.key = now
                host.pressed(now, true)
                scheduleLongPress(p)
            }
        }
    }

    private fun cancelLongPress(p: Pointer) {
        if (longPressFor === p) {
            handler.removeCallbacks(longPress)
            longPressFor = null
        }
    }

    private fun up(p: Pointer) {
        cancelLongPress(p)
        if (repeating === p) {
            handler.removeCallbacks(repeatTick)
            repeating = null
        }
        when (p.mode) {
            Mode.PRESS -> {
                host.released()
                host.tap(p.key)
            }
            Mode.POPUP -> {
                val t = view.popupText()
                view.closePopup()
                if (t != null) host.popupPicked(t)
            }
            Mode.SWIPE -> host.swiped(p.xs.copyOf(p.n), p.ys.copyOf(p.n))
            Mode.BACKSPACE -> host.deleteSelectedWords()
            Mode.SPACE, Mode.REPEAT, Mode.DONE -> host.released()
        }
        if (pointers.isEmpty()) host.released()
    }

    companion object {
        const val LONG_PRESS_MS = 350L
        const val REPEAT_START_MS = 400L
        const val REPEAT_MS = 80L
        const val REPEAT_FAST_MS = 35L
        const val REPEAT_FAST_AFTER = 8
        const val CURSOR_STEP_DP = 12f

        /** Time since boot, the clock MotionEvent uses. */
        fun now(): Long = SystemClock.uptimeMillis()
    }
}
