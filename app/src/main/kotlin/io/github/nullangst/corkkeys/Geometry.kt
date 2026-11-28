/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import kotlin.math.hypot
import kotlin.math.min

/** A rectangle in pixels. Plain floats, so the maths runs and is tested off the phone. */
class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom

    /** How far a point is from this box; 0 inside it. */
    fun distance(x: Float, y: Float): Float {
        val dx = maxOf(left - x, 0f, x - right)
        val dy = maxOf(top - y, 0f, y - bottom)
        return hypot(dx, dy)
    }
}

/**
 * A key on screen. [box] is the key's own cell, drawn with a small gap inside it. [hit] is the
 * area that types it: the same cell, except the first and last key of a row reach the edges, so
 * a short row like A to L has no dead space at its ends.
 */
class PlacedKey(val key: Key, val box: Box, val hit: Box, val row: Int)

enum class KeyHeight(val dp: Float) { SHORT(48f), NORMAL(54f), TALL(60f) }

enum class Mode { DOCKED, ONE_LEFT, ONE_RIGHT, FLOATING }

/**
 * What the keyboard has to fit into: the window's size and the parts of it covered by the
 * navigation bar and display cutouts.
 */
class Frame(
    val windowWidth: Int,
    val windowHeight: Int,
    val density: Float,
    val insetLeft: Int = 0,
    val insetRight: Int = 0,
    val insetBottom: Int = 0,
) {
    val landscape: Boolean get() = windowWidth > windowHeight
    val usableWidth: Int get() = (windowWidth - insetLeft - insetRight).coerceAtLeast(1)
}

/** Every key's place, and the keyboard area's size. */
class Plan(val keys: List<PlacedKey>, val width: Float, val height: Float, val unit: Float) {
    /** The key under a finger, or the nearest one when the finger is between keys. */
    fun find(x: Float, y: Float): PlacedKey? {
        keys.firstOrNull { it.hit.contains(x, y) }?.let { return it }
        return keys.minByOrNull { it.hit.distance(x, y) }
    }
}

object Geometry {
    /** One-handed keys take this share of the width; the rest holds the flip and back buttons. */
    const val ONE_HANDED_FRACTION = 0.8f

    /** The keys never take more than this share of the window's height. */
    const val MAX_HEIGHT_FRACTION = 0.42f
    const val FLOAT_MAX_HEIGHT_FRACTION = 0.34f

    const val FLOAT_MAX_WIDTH_DP = 400f
    const val FLOAT_SCALE = 0.85f
    const val PAD_DP = 3f

    fun floatingWidth(f: Frame): Float =
        min(f.usableWidth * (if (f.landscape) 0.55f else 0.8f), FLOAT_MAX_WIDTH_DP * f.density)

    /**
     * Lays out [layout]. Height is set by [rowsForHeight] rows of the chosen height, so a symbol
     * page with one row more than the letters squeezes its rows into the same space instead of
     * making the keyboard jump.
     */
    fun plan(layout: Layout, frame: Frame, height: KeyHeight, mode: Mode, rowsForHeight: Int = layout.rows.size): Plan {
        val d = frame.density
        val usable = frame.usableWidth.toFloat()
        val floating = mode == Mode.FLOATING
        val planWidth: Float
        val areaLeft: Float
        val areaWidth: Float
        when (mode) {
            Mode.DOCKED -> {
                planWidth = frame.windowWidth.toFloat(); areaLeft = frame.insetLeft.toFloat(); areaWidth = usable
            }
            Mode.ONE_LEFT -> {
                planWidth = frame.windowWidth.toFloat(); areaLeft = frame.insetLeft.toFloat(); areaWidth = usable * ONE_HANDED_FRACTION
            }
            Mode.ONE_RIGHT -> {
                planWidth = frame.windowWidth.toFloat()
                areaWidth = usable * ONE_HANDED_FRACTION
                areaLeft = frame.insetLeft + usable - areaWidth
            }
            Mode.FLOATING -> {
                planWidth = floatingWidth(frame); areaLeft = 0f; areaWidth = planWidth
            }
        }

        val pad = PAD_DP * d
        var rowH = height.dp * d * (if (floating) FLOAT_SCALE else 1f)
        val maxKeys = frame.windowHeight * (if (floating) FLOAT_MAX_HEIGHT_FRACTION else MAX_HEIGHT_FRACTION)
        if (rowH * rowsForHeight > maxKeys) rowH = maxKeys / rowsForHeight
        val keysHeight = rowH * rowsForHeight
        val actualRowH = keysHeight / layout.rows.size
        val units = layout.units
        val unit = areaWidth / units

        val placed = ArrayList<PlacedKey>()
        layout.rows.forEachIndexed { r, row ->
            val rowUnits = row.sumOf { it.width.toDouble() }.toFloat()
            var x = areaLeft + (units - rowUnits) / 2f * unit
            val top = pad + r * actualRowH
            val bottom = top + actualRowH
            row.forEachIndexed { i, key ->
                val right = x + key.width * unit
                val box = Box(x, top, right, bottom)
                val hitLeft = if (i == 0) areaLeft else x
                val hitRight = if (i == row.lastIndex) areaLeft + areaWidth else right
                placed += PlacedKey(key, box, Box(hitLeft, top, hitRight, bottom), r)
                x = right
            }
        }

        // One-handed: the flip and back buttons fill the empty side, top half and bottom half.
        if (mode == Mode.ONE_LEFT || mode == Mode.ONE_RIGHT) {
            val sideLeft = if (mode == Mode.ONE_LEFT) areaLeft + areaWidth else frame.insetLeft.toFloat()
            val sideRight = if (mode == Mode.ONE_LEFT) frame.insetLeft + usable else areaLeft
            val mid = pad + keysHeight / 2f
            val flip = Box(sideLeft, pad, sideRight, mid)
            val exit = Box(sideLeft, mid, sideRight, pad + keysHeight)
            placed += PlacedKey(Key("", kind = Kind.ONE_HANDED_FLIP), flip, flip, -1)
            placed += PlacedKey(Key("", kind = Kind.ONE_HANDED_EXIT), exit, exit, -1)
        }

        val bottomInset = if (floating) 0f else frame.insetBottom.toFloat()
        return Plan(placed, planWidth, pad + keysHeight + pad + bottomInset, unit)
    }
}

/**
 * Where each letter sits on a layout, in key widths, for the corrector's "neighbouring keys"
 * and the swipe decoder's key centres. Accented letters map to their plain key.
 */
class KeyMap private constructor(private val pos: Map<Char, FloatArray>, val rowHeight: Float) {
    fun center(c: Char): FloatArray? = pos[c] ?: pos[Chars.base(c)]

    fun near(a: Char, b: Char): Boolean {
        val p = center(a) ?: return false
        val q = center(b) ?: return false
        val dx = p[0] - q[0]
        val dy = p[1] - q[1]
        return dx * dx + dy * dy <= NEAR_KEYS * NEAR_KEYS
    }

    val letters: Set<Char> get() = pos.keys

    companion object {
        /** Keys whose centres are this many key widths apart or closer are neighbours. */
        const val NEAR_KEYS = 1.6f

        fun of(layout: Layout, plan: Plan = Geometry.plan(layout, Frame(1080, 2400, 2.75f), KeyHeight.NORMAL, Mode.DOCKED)): KeyMap {
            val u = plan.unit
            val map = HashMap<Char, FloatArray>()
            var rowH = 1f
            for (k in plan.keys) {
                if (!k.key.isLetter) continue
                map[k.key.output[0]] = floatArrayOf(k.box.cx / u, k.box.cy / u)
                rowH = k.box.height / u
            }
            return KeyMap(map, rowH)
        }
    }
}
