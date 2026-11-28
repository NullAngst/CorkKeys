/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The long-press chooser: the key's symbol first, then its accents or variants, in a row above
 * the key. The first entry is picked to start with, so long-press and release types the symbol;
 * slide sideways to pick another. Coordinates are the keyboard view's.
 */
class LongPressPopup {
    var options: List<String> = emptyList()
        private set
    var selected = 0
        private set
    private var left = 0f
    private var top = 0f
    private var cell = 0f
    private var cellH = 0f

    /** Icons drawn in place of text, for entries that are actions (the comma's settings and emoji). */
    var icons: Map<String, Drawable> = emptyMap()

    val showing: Boolean get() = options.isNotEmpty()
    val text: String? get() = options.getOrNull(selected)

    /**
     * Opens over [anchor]. Long lists wrap to a second row below the first; the cells line up
     * with the key so the first entry sits right above the finger, moved in to fit [width].
     */
    fun show(list: List<String>, anchor: Box, width: Float) {
        options = list
        selected = 0
        cell = anchor.width.coerceAtMost(anchor.height * 1.1f).coerceAtLeast(anchor.height * 0.7f)
        cellH = anchor.height * 1.05f
        val perRow = perRow()
        val rows = (list.size + perRow - 1) / perRow
        val total = perRow.coerceAtMost(list.size) * cell
        left = (anchor.cx - cell / 2f).coerceAtMost(width - total).coerceAtLeast(0f)
        top = anchor.top - cellH * rows
    }

    private fun perRow() = MAX_PER_ROW

    fun hide() {
        options = emptyList()
    }

    /** Follows the finger. Returns true when the pick changed. */
    fun select(x: Float, y: Float): Boolean {
        if (!showing) return false
        val perRow = perRow()
        val col = ((x - left) / cell).toInt().coerceIn(0, perRow - 1)
        val rows = (options.size + perRow - 1) / perRow
        val row = ((y - top) / cellH).toInt().coerceIn(0, rows - 1)
        val i = (row * perRow + col).coerceIn(0, options.size - 1)
        if (i == selected) return false
        selected = i
        return true
    }

    fun draw(c: Canvas, dx: Float, dy: Float, pal: Palette, density: Float, bg: Paint, label: Paint) {
        if (!showing) return
        val perRow = perRow()
        val rows = (options.size + perRow - 1) / perRow
        val cols = perRow.coerceAtMost(options.size)
        val frame = RectF(dx + left, dy + top, dx + left + cols * cell, dy + top + rows * cellH)
        val radius = 10f * density
        bg.color = pal.surface
        c.drawRoundRect(frame, radius, radius, bg)
        bg.color = pal.line
        bg.style = Paint.Style.STROKE
        bg.strokeWidth = density
        c.drawRoundRect(frame, radius, radius, bg)
        bg.style = Paint.Style.FILL
        label.textAlign = Paint.Align.CENTER
        label.textSize = cellH * 0.42f
        options.forEachIndexed { i, s ->
            val l = frame.left + (i % perRow) * cell
            val t = frame.top + (i / perRow) * cellH
            val r = RectF(l, t, l + cell, t + cellH)
            if (i == selected) {
                bg.color = pal.accent
                c.drawRoundRect(r, radius * 0.8f, radius * 0.8f, bg)
            }
            label.color = if (i == selected) pal.onAccent else pal.text
            val ic = icons[s]
            if (ic != null) {
                val half = (cellH * 0.24f).toInt()
                val cx = r.centerX().toInt()
                val cy = r.centerY().toInt()
                ic.setBounds(cx - half, cy - half, cx + half, cy + half)
                ic.setTint(label.color)
                ic.draw(c)
            } else {
                c.drawText(s, r.centerX(), r.centerY() - (label.descent() + label.ascent()) / 2f, label)
            }
        }
    }

    companion object {
        const val MAX_PER_ROW = 8
    }
}
