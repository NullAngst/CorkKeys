/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * The bubble above a pressed key, showing what it types. Drawn on the overlay above the
 * keyboard, so it can rise above the top row. Never shown in password fields: someone looking
 * over your shoulder would read the password off it.
 */
class KeyPreview {
    var key: PlacedKey? = null
        private set
    var text: String = ""
        private set

    fun show(k: PlacedKey, label: String) {
        key = k
        text = label
    }

    fun hide() {
        key = null
    }

    /** [dx], [dy]: where the keyboard view sits on the overlay. [maxWidth]: the overlay's width. */
    fun draw(c: Canvas, dx: Float, dy: Float, maxWidth: Float, pal: Palette, density: Float, bg: Paint, label: Paint) {
        val k = key ?: return
        val b = k.box
        val w = maxOf(b.width * 1.15f, 44f * density)
        val h = b.height * 1.15f
        var left = dx + b.cx - w / 2f
        left = left.coerceIn(0f, maxOf(0f, maxWidth - w))
        val bottom = dy + b.top + 4f * density
        val r = RectF(left, bottom - h, left + w, bottom)
        bg.color = pal.surface
        c.drawRoundRect(r, 10f * density, 10f * density, bg)
        bg.color = pal.line
        bg.style = Paint.Style.STROKE
        bg.strokeWidth = density
        c.drawRoundRect(r, 10f * density, 10f * density, bg)
        bg.style = Paint.Style.FILL
        label.color = pal.text
        label.textSize = h * 0.5f
        label.textAlign = Paint.Align.CENTER
        c.drawText(text, r.centerX(), r.centerY() - (label.descent() + label.ascent()) / 2f, label)
    }
}
