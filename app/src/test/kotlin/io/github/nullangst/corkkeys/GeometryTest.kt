/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    private val eps = 0.01f

    /** Phone sizes: portrait, landscape with the nav bar on the side, and a cutout. */
    private val frames = listOf(
        Frame(1080, 2400, 2.75f),
        Frame(1080, 2400, 2.75f, insetBottom = 132),
        Frame(2400, 1080, 2.75f),
        Frame(2400, 1080, 2.75f, insetLeft = 0, insetRight = 132),
        Frame(2400, 1080, 2.75f, insetLeft = 110, insetRight = 132, insetBottom = 66),
        Frame(1440, 3120, 3.5f, insetLeft = 0, insetRight = 0, insetBottom = 66),
    )

    private val layouts = listOf("en", "de", "es").flatMap {
        listOf(Letters.build(Letters.data(it), false), Letters.build(Letters.data(it), true), LayoutSymbols.page1(it), LayoutSymbols.page2(it))
    } + listOf(LayoutNumber.build(false), LayoutPhone.build())

    private fun checkRowsFill(plan: Plan, left: Float, right: Float, what: String) {
        val rows = plan.keys.filter { it.row >= 0 }.groupBy { it.row }
        for ((r, keys) in rows) {
            val sorted = keys.sortedBy { it.hit.left }
            assertEquals("$what row $r starts at the edge", left, sorted.first().hit.left, eps)
            assertEquals("$what row $r ends at the edge", right, sorted.last().hit.right, eps)
            for (i in 1 until sorted.size) {
                assertEquals("$what row $r has no gap or overlap", sorted[i - 1].hit.right, sorted[i].hit.left, eps)
            }
            for (k in keys) assertTrue("$what: drawn key inside its hit area", k.box.left >= k.hit.left - eps && k.box.right <= k.hit.right + eps)
        }
    }

    @Test
    fun keysFillTheUsableWidth() {
        for (f in frames) for (h in KeyHeight.entries) for (l in layouts) {
            val p = Geometry.plan(l, f, h, Mode.DOCKED)
            assertEquals(f.windowWidth.toFloat(), p.width, eps)
            checkRowsFill(p, f.insetLeft.toFloat(), (f.windowWidth - f.insetRight).toFloat(), "${l.id} ${f.windowWidth}x${f.windowHeight} $h")
        }
    }

    @Test
    fun heightsDifferAndRespectTheWindow() {
        val f = Frame(1080, 2400, 2.75f)
        val l = Letters.build(LayoutEnglish.data, false)
        val short = Geometry.plan(l, f, KeyHeight.SHORT, Mode.DOCKED).height
        val normal = Geometry.plan(l, f, KeyHeight.NORMAL, Mode.DOCKED).height
        val tall = Geometry.plan(l, f, KeyHeight.TALL, Mode.DOCKED).height
        assertTrue(short < normal && normal < tall)
        for (fr in frames) for (h in KeyHeight.entries) {
            val p = Geometry.plan(l, fr, h, Mode.DOCKED)
            assertTrue("keys stay under the height cap", p.height - fr.insetBottom <= fr.windowHeight * Geometry.maxKeysFraction(fr) + 6 * 3.5f * fr.density)
            if (fr.landscape) assertTrue("landscape leaves the app most of the screen", p.height - fr.insetBottom <= fr.windowHeight * 0.36f)
        }
    }

    @Test
    fun bottomInsetSitsBelowTheKeys() {
        val l = Letters.build(LayoutEnglish.data, false)
        val a = Geometry.plan(l, Frame(1080, 2400, 2.75f), KeyHeight.NORMAL, Mode.DOCKED)
        val b = Geometry.plan(l, Frame(1080, 2400, 2.75f, insetBottom = 132), KeyHeight.NORMAL, Mode.DOCKED)
        assertEquals(a.height + 132, b.height, eps)
        assertEquals(a.keys.maxOf { it.box.bottom }, b.keys.maxOf { it.box.bottom }, eps)
    }

    @Test
    fun symbolPagesKeepTheKeyboardHeight() {
        val f = Frame(1080, 2400, 2.75f)
        for (lang in listOf("en", "de", "es")) {
            val letters = Letters.build(Letters.data(lang), false)
            val sym = LayoutSymbols.page1(lang)
            val a = Geometry.plan(letters, f, KeyHeight.NORMAL, Mode.DOCKED, letters.rows.size)
            val b = Geometry.plan(sym, f, KeyHeight.NORMAL, Mode.DOCKED, letters.rows.size)
            assertEquals(a.height, b.height, eps)
        }
    }

    @Test
    fun oneHandedBothSides() {
        for (f in frames) for (l in layouts) {
            val usable = f.usableWidth.toFloat()
            val keysW = usable * Geometry.ONE_HANDED_FRACTION
            val left = Geometry.plan(l, f, KeyHeight.NORMAL, Mode.ONE_LEFT)
            checkRowsFill(left, f.insetLeft.toFloat(), f.insetLeft + keysW, "${l.id} one-handed left")
            val right = Geometry.plan(l, f, KeyHeight.NORMAL, Mode.ONE_RIGHT)
            checkRowsFill(right, f.insetLeft + usable - keysW, f.insetLeft + usable, "${l.id} one-handed right")
            for (p in listOf(left, right)) {
                val side = p.keys.filter { it.row < 0 }
                assertEquals(setOf(Kind.ONE_HANDED_FLIP, Kind.ONE_HANDED_EXIT), side.map { it.key.kind }.toSet())
                for (s in side) assertEquals(usable - keysW, s.box.width, eps)
            }
        }
    }

    @Test
    fun floatingIsSmallerAndSelfContained() {
        for (f in frames) for (l in layouts) {
            val p = Geometry.plan(l, f, KeyHeight.NORMAL, Mode.FLOATING)
            val docked = Geometry.plan(l, f, KeyHeight.NORMAL, Mode.DOCKED)
            assertTrue(p.width < f.usableWidth)
            assertTrue(p.height <= docked.height)
            checkRowsFill(p, 0f, p.width, "${l.id} floating")
        }
    }

    @Test
    fun findPicksTheNearestKeyBetweenKeys() {
        val l = Letters.build(LayoutEnglish.data, false)
        val p = Geometry.plan(l, Frame(1080, 2400, 2.75f), KeyHeight.NORMAL, Mode.DOCKED)
        val q = p.keys.first { it.key.label == "q" }
        assertEquals("q", p.find(q.box.cx, q.box.cy)?.key?.label)
        assertEquals("q", p.find(q.box.cx, q.box.top - 50f)?.key?.label)
        val a = p.keys.first { it.key.label == "a" }
        assertEquals("a", p.find(1f, a.box.cy)?.key?.label)
    }
}
