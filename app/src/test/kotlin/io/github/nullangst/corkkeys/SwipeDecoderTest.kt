/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Synthetic swipes for the 500 most common words of each language: a path through the word's
 * key centres, points every tenth of a key, with random wobble from a fixed seed so the test
 * gives the same answer every run. The pass mark is the definition of done: at least 90% come
 * out first and at least 98% land in the top three.
 */
class SwipeDecoderTest {
    class Score(val top1: Int, val top3: Int, val total: Int, val misses: List<String>, val worstMs: Double, val meanMs: Double)

    companion object {
        const val WORDS = 500
        const val WOBBLE = 0.18

        fun synthetic(word: String, keys: KeyMap, rnd: Random): Pair<FloatArray, FloatArray>? {
            val centers = ArrayList<FloatArray>()
            for (c in word) {
                if (!c.isLetter()) continue
                val k = keys.center(c.lowercaseChar()) ?: keys.center(Chars.base(c)) ?: return null
                if (centers.isEmpty() || !centers.last().contentEquals(k)) centers += k
            }
            if (centers.size < 2) return null
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            // The finger starts and ends a little off centre, like a real one.
            fun wob() = (rnd.nextGaussian() * WOBBLE).toFloat()
            for (i in 0 until centers.size - 1) {
                val a = centers[i]
                val b = centers[i + 1]
                val len = kotlin.math.hypot(b[0] - a[0], b[1] - a[1])
                val steps = maxOf(2, (len / 0.1f).toInt())
                val ox = wob()
                val oy = wob()
                for (s in 0 until steps) {
                    val t = s.toFloat() / steps
                    // The wobble drifts smoothly along each stroke instead of jumping per point.
                    xs += a[0] + (b[0] - a[0]) * t + ox * (1 - t) + wob() * 0.25f
                    ys += a[1] + (b[1] - a[1]) * t + oy * (1 - t) + wob() * 0.25f
                }
            }
            val last = centers.last()
            xs += last[0] + wob()
            ys += last[1] + wob()
            return xs.toFloatArray() to ys.toFloatArray()
        }

        fun run(lang: String): Score {
            val d = TestWords.get(lang)
            val keys = TestWords.keys(lang)
            val dec = SwipeDecoder(listOf(d), keys)
            val rnd = Random(20261127L + lang.hashCode())
            var top1 = 0
            var top3 = 0
            var total = 0
            var worst = 0.0
            var sum = 0.0
            val misses = ArrayList<String>()
            var id = 0
            val seen = HashSet<String>()
            while (total < WORDS && id < d.size) {
                val w = d.word(id++)
                if (w.length < 2 || !w.all { it.isLetter() } || !seen.add(w.lowercase())) continue
                val (xs, ys) = synthetic(w, keys, rnd) ?: continue
                val t0 = System.nanoTime()
                val out = dec.decode(xs, ys, 3)
                val ms = (System.nanoTime() - t0) / 1e6
                if (total > 20) worst = maxOf(worst, ms)
                sum += ms
                total++
                val words = out.map { it.word.lowercase() }
                if (words.firstOrNull() == w.lowercase()) top1++ else misses += "$w->${words.joinToString("/")}"
                if (w.lowercase() in words) top3++
            }
            return Score(top1, top3, total, misses, worst, sum / total)
        }
    }

    private fun check(lang: String) {
        val s = run(lang)
        val msg = "$lang: top1 ${s.top1}/${s.total}, top3 ${s.top3}/${s.total}, mean ${"%.1f".format(s.meanMs)} ms. First misses: ${s.misses.take(25)}"
        println(msg)
        assertEquals(WORDS, s.total)
        assertTrue(msg, s.top1 >= s.total * 0.90)
        assertTrue(msg, s.top3 >= s.total * 0.98)
    }

    @Test
    fun english() = check("en")

    @Test
    fun german() = check("de")

    @Test
    fun spanish() = check("es")

    @Test
    fun resampleSpacesPointsEvenly() {
        val ox = FloatArray(SwipeDecoder.POINTS)
        val oy = FloatArray(SwipeDecoder.POINTS)
        val len = SwipeDecoder.resample(floatArrayOf(0f, 10f, 10f), floatArrayOf(0f, 0f, 10f), 3, ox, oy)
        assertEquals(20f, len, 0.001f)
        assertEquals(0f, ox[0], 0.001f)
        assertEquals(10f, ox[SwipeDecoder.POINTS - 1], 0.001f)
        assertEquals(10f, oy[SwipeDecoder.POINTS - 1], 0.001f)
        for (i in 1 until SwipeDecoder.POINTS) {
            val step = kotlin.math.hypot(ox[i] - ox[i - 1], oy[i] - oy[i - 1])
            assertTrue("step $i is $step", step < 20f / (SwipeDecoder.POINTS - 1) + 0.01f)
        }
    }
}
