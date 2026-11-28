/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeProvider

/**
 * One custom View that draws every key, bordered or flat. There is no View per key: keys are
 * rectangles from [Geometry], drawn in one pass, with touches handled by [Touch].
 */
@SuppressLint("ViewConstructor")
class KeyboardView(ctx: Context, host: TouchHost) : View(ctx) {
    var plan: Plan? = null
        private set
    private var layout: Layout? = null
    private var frame: Frame? = null
    private var rowDp = KeyHeight.NORMAL.dp
    private var mode = Mode.DOCKED
    private var rowsForHeight = 4

    var pal: Palette = Themes.light
    var bordered = true
    var shift: Shift.State = Shift.State.OFF
    var spaceLabel = ""
    var enter = Enter.NEWLINE

    /** The keys drawn pressed right now. */
    val pressed = HashSet<PlacedKey>()

    val preview = KeyPreview()
    val popup = LongPressPopup()

    /** Draws the swipe trail over the keys. Set by the swipe code; null while swipe is off. */
    var trail: ((Canvas) -> Unit)? = null

    /** The view above everything in the keyboard window that bubbles and popups are drawn on. */
    var overlay: Overlay? = null

    val touch = Touch(this, host)
    var accessibility: KeyAccessibility? = null

    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    init {
        isHapticFeedbackEnabled = true
        val icons = HashMap<String, android.graphics.drawable.Drawable>()
        ctx.getDrawable(R.drawable.ic_settings)?.mutate()?.let { icons[Key.ACTION_SETTINGS] = it }
        ctx.getDrawable(R.drawable.ic_emoji)?.mutate()?.let { icons[Key.ACTION_EMOJI] = it }
        popup.icons = icons
    }

    fun dp(v: Float) = v * resources.displayMetrics.density

    fun setLayout(l: Layout, f: Frame, rowHeightDp: Float, m: Mode, rows: Int) {
        layout = l
        frame = f
        rowDp = rowHeightDp
        mode = m
        rowsForHeight = rows
        replan()
    }

    fun setFrame(f: Frame) {
        frame = f
        replan()
    }

    private fun replan() {
        val l = layout ?: return
        val f = frame ?: return
        touch.cancelAll()
        plan = Geometry.plan(l, f, rowDp, mode, rowsForHeight)
        accessibility?.invalidateAll()
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val p = plan
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(if (p != null && mode == Mode.FLOATING) p.width.toInt() else w, p?.height?.toInt() ?: 0)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = touch.onTouchEvent(event)

    override fun onHoverEvent(event: MotionEvent): Boolean =
        accessibility?.onHover(event) ?: super.onHoverEvent(event)

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider? = accessibility ?: super.getAccessibilityNodeProvider()

    fun openPopup(k: PlacedKey) {
        val list = if (shift != Shift.State.OFF && k.key.isLetter) Key.shiftPopup(k.key.popup) else k.key.popup
        popup.show(list, k.box, width.toFloat())
        invalidateOverlay()
    }

    fun popupSelect(x: Float, y: Float) = popup.select(x, y)
    fun popupText(): String? = popup.text

    fun closePopup() {
        if (!popup.showing) return
        popup.hide()
        invalidateOverlay()
    }

    fun invalidateOverlay() {
        overlay?.invalidate()
    }

    /** What a key shows, with shift applied. */
    fun labelOf(k: Key): String = if (k.isLetter && shift != Shift.State.OFF) k.shifted() else k.label

    override fun onDraw(canvas: Canvas) {
        val p = plan ?: return
        canvas.drawColor(pal.keyboardBg)
        val radius = dp(6f)
        val gapX = dp(if (mode == Mode.FLOATING) 2.5f else 3f)
        val gapY = dp(4f)
        for (pk in p.keys) {
            val k = pk.key
            val b = pk.box
            rect.set(b.left + gapX, b.top + gapY, b.right - gapX, b.bottom - gapY)
            val isPressed = pk in pressed
            val function = k.kind != Kind.CHAR && k.kind != Kind.SPACE && k.kind != Kind.ENTER
            val fill = when {
                k.kind == Kind.ENTER -> pal.accent
                isPressed -> pal.keyPressed
                !bordered -> null
                function -> pal.keyFunction
                else -> pal.key
            }
            if (fill != null) {
                face.color = fill
                if (k.kind == Kind.ENTER && !bordered) {
                    val r = minOf(rect.width(), rect.height()) / 2f
                    canvas.drawRoundRect(rect.centerX() - r * 1.3f, rect.centerY() - r, rect.centerX() + r * 1.3f, rect.centerY() + r, r, r, face)
                } else {
                    canvas.drawRoundRect(rect, radius, radius, face)
                }
            }
            drawContent(canvas, pk, rect)
        }
        trail?.invoke(canvas)
    }

    private fun drawContent(c: Canvas, pk: PlacedKey, r: RectF) {
        val k = pk.key
        val h = r.height()
        val fg = if (k.kind == Kind.ENTER) pal.onAccent else pal.text
        icon.color = fg
        icon.strokeWidth = dp(1.8f)
        val s = minOf(h, r.width()) * 0.42f
        when (k.kind) {
            Kind.SHIFT -> drawShift(c, r, s)
            Kind.BACKSPACE -> drawBackspace(c, r, s)
            Kind.ENTER -> drawEnter(c, r, s)
            Kind.ONE_HANDED_FLIP -> drawFlip(c, r, s)
            Kind.ONE_HANDED_EXIT -> drawExpand(c, r, s)
            Kind.SPACE -> {
                small.color = pal.textDim
                small.textSize = h * 0.3f
                small.typeface = Typeface.DEFAULT
                c.drawText(spaceLabel, r.centerX(), r.centerY() - (small.descent() + small.ascent()) / 2f, small)
            }
            else -> {
                val text = labelOf(k)
                label.color = fg
                label.typeface = Typeface.DEFAULT
                label.textSize = if (text.length > 1 && k.kind != Kind.CHAR) h * 0.3f else if (text.length > 2) h * 0.3f else h * 0.42f
                val corner = k.corner
                // Phone pad: digits sit a little high, their letters underneath.
                val wide = r.width() > h * 1.6f
                if (corner != null && Key.ACTION_EMOJI in k.popup) {
                    // The comma: a small :) right above it, saying a long-press has emoji.
                    c.drawText(text, r.centerX(), r.centerY() + h * 0.08f - (label.descent() + label.ascent()) / 2f, label)
                    small.color = pal.textDim
                    small.textSize = h * 0.2f
                    c.drawText(corner, r.centerX(), r.top + h * 0.32f, small)
                } else if (corner != null && wide) {
                    c.drawText(text, r.centerX(), r.centerY() - h * 0.05f - (label.descent() + label.ascent()) / 2f, label)
                    small.color = pal.textDim
                    small.textSize = h * 0.2f
                    c.drawText(corner, r.centerX() + r.width() * 0.3f, r.top + h * 0.3f, small)
                } else {
                    c.drawText(text, r.centerX(), r.centerY() - (label.descent() + label.ascent()) / 2f, label)
                    if (corner != null) {
                        small.color = pal.textDim
                        small.textSize = h * 0.22f
                        c.drawText(corner, r.right - h * 0.17f, r.top + h * 0.27f, small)
                    }
                }
            }
        }
    }

    private fun drawShift(c: Canvas, r: RectF, s: Float) {
        val cx = r.centerX()
        val cy = r.centerY()
        path.reset()
        path.moveTo(cx, cy - s * 0.55f)
        path.lineTo(cx + s * 0.5f, cy)
        path.lineTo(cx + s * 0.22f, cy)
        path.lineTo(cx + s * 0.22f, cy + s * 0.4f)
        path.lineTo(cx - s * 0.22f, cy + s * 0.4f)
        path.lineTo(cx - s * 0.22f, cy)
        path.lineTo(cx - s * 0.5f, cy)
        path.close()
        icon.style = if (shift == Shift.State.OFF) Paint.Style.STROKE else Paint.Style.FILL_AND_STROKE
        c.drawPath(path, icon)
        icon.style = Paint.Style.STROKE
        if (shift == Shift.State.LOCK) c.drawLine(cx - s * 0.3f, cy + s * 0.62f, cx + s * 0.3f, cy + s * 0.62f, icon)
    }

    private fun drawBackspace(c: Canvas, r: RectF, s: Float) {
        val cx = r.centerX()
        val cy = r.centerY()
        path.reset()
        path.moveTo(cx - s * 0.7f, cy)
        path.lineTo(cx - s * 0.3f, cy - s * 0.4f)
        path.lineTo(cx + s * 0.6f, cy - s * 0.4f)
        path.lineTo(cx + s * 0.6f, cy + s * 0.4f)
        path.lineTo(cx - s * 0.3f, cy + s * 0.4f)
        path.close()
        c.drawPath(path, icon)
        c.drawLine(cx - s * 0.05f, cy - s * 0.17f, cx + s * 0.3f, cy + s * 0.17f, icon)
        c.drawLine(cx + s * 0.3f, cy - s * 0.17f, cx - s * 0.05f, cy + s * 0.17f, icon)
    }

    private fun drawEnter(c: Canvas, r: RectF, s: Float) {
        val cx = r.centerX()
        val cy = r.centerY()
        when (enter) {
            Enter.SEARCH -> {
                c.drawCircle(cx - s * 0.1f, cy - s * 0.1f, s * 0.3f, icon)
                c.drawLine(cx + s * 0.12f, cy + s * 0.12f, cx + s * 0.42f, cy + s * 0.42f, icon)
            }
            Enter.SEND -> {
                path.reset()
                path.moveTo(cx - s * 0.45f, cy - s * 0.4f)
                path.lineTo(cx + s * 0.5f, cy)
                path.lineTo(cx - s * 0.45f, cy + s * 0.4f)
                path.lineTo(cx - s * 0.3f, cy)
                path.close()
                c.drawPath(path, icon)
            }
            Enter.DONE -> {
                path.reset()
                path.moveTo(cx - s * 0.4f, cy)
                path.lineTo(cx - s * 0.1f, cy + s * 0.3f)
                path.lineTo(cx + s * 0.45f, cy - s * 0.3f)
                c.drawPath(path, icon)
            }
            Enter.GO, Enter.NEXT -> arrow(c, cx, cy, s, 1f)
            Enter.PREVIOUS -> arrow(c, cx, cy, s, -1f)
            Enter.NEWLINE -> {
                path.reset()
                path.moveTo(cx + s * 0.45f, cy - s * 0.35f)
                path.lineTo(cx + s * 0.45f, cy + s * 0.1f)
                path.lineTo(cx - s * 0.45f, cy + s * 0.1f)
                c.drawPath(path, icon)
                c.drawLine(cx - s * 0.45f, cy + s * 0.1f, cx - s * 0.2f, cy - s * 0.15f, icon)
                c.drawLine(cx - s * 0.45f, cy + s * 0.1f, cx - s * 0.2f, cy + s * 0.35f, icon)
            }
        }
    }

    private fun arrow(c: Canvas, cx: Float, cy: Float, s: Float, dir: Float) {
        c.drawLine(cx - s * 0.45f * dir, cy, cx + s * 0.45f * dir, cy, icon)
        c.drawLine(cx + s * 0.45f * dir, cy, cx + s * 0.15f * dir, cy - s * 0.3f, icon)
        c.drawLine(cx + s * 0.45f * dir, cy, cx + s * 0.15f * dir, cy + s * 0.3f, icon)
    }

    private fun drawFlip(c: Canvas, r: RectF, s: Float) {
        val dir = if (mode == Mode.ONE_LEFT) 1f else -1f
        arrow(c, r.centerX(), r.centerY(), s, dir)
    }

    private fun drawExpand(c: Canvas, r: RectF, s: Float) {
        val cx = r.centerX()
        val cy = r.centerY()
        c.drawRect(cx - s * 0.45f, cy - s * 0.3f, cx + s * 0.45f, cy + s * 0.3f, icon)
        c.drawLine(cx - s * 0.25f, cy, cx + s * 0.25f, cy, icon)
    }

    /**
     * Draws the bubble and the long-press popup above the keyboard. Covers the whole keyboard
     * window but takes no touches, so they go straight through to whatever is underneath.
     */
    class Overlay(ctx: Context, private val keys: KeyboardView) : View(ctx) {
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG)
        private val here = IntArray(2)
        private val there = IntArray(2)

        init {
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean = false

        override fun onDraw(canvas: Canvas) {
            if (keys.preview.key == null && !keys.popup.showing) return
            getLocationInWindow(here)
            keys.getLocationInWindow(there)
            val dx = (there[0] - here[0]).toFloat()
            val dy = (there[1] - here[1]).toFloat()
            val d = resources.displayMetrics.density
            keys.preview.draw(canvas, dx, dy, width.toFloat(), keys.pal, d, bg, text)
            keys.popup.draw(canvas, dx, dy, keys.pal, d, bg, text)
        }
    }
}
