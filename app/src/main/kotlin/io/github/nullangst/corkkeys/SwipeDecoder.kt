/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Swipe typing: a finger path in, ranked words out. Shape matching, the method from the SHARK2
 * paper (Kristensson and Zhai, 2004), written from scratch, plain Kotlin, no model files.
 *
 * 1. The path is resampled to [POINTS] points spaced evenly along its length.
 * 2. Shortlist: only words whose first letter is near where the swipe started and whose last
 *    letter is near where it ended, from an index by first and last key.
 * 3. Each shortlisted word gets the path a perfect swipe would take through its key centres
 *    (double letters once, so "hello" is h-e-l-o), resampled the same way.
 * 4. Score: shape distance (both paths scaled and centred, compared point by point), location
 *    distance (how far the path strays from the keys the word needs), and the word's frequency.
 *
 * Everything here works in key widths: positions come from [KeyMap], path points are divided by
 * the on-screen key width before they arrive.
 */
class SwipeDecoder(private val dicts: List<Dictionary>, private val keys: KeyMap) {
    class Result(val word: String, val score: Float, val offensive: Boolean)

    /** Per dictionary: word ids by (first key, last key) of their key path. */
    private class Index(val buckets: HashMap<Int, IntArray>)

    private val indexes: List<Index> = dicts.map { index(it) }

    /** The key a letter is typed on in this layout: its own key if it has one, else its plain letter's. */
    private fun keyOf(c: Char): Char? {
        val l = c.lowercaseChar()
        if (keys.center(l) != null && l in keys.letters) return l
        val b = Chars.base(c)
        return if (b in keys.letters) b else null
    }

    /** A word's key path with repeats collapsed, or null if a letter has no key or it's one key long. */
    private fun path(word: String): CharArray? {
        val out = CharArray(word.length)
        var n = 0
        for (c in word) {
            if (!c.isLetter()) {
                if (c == '\'' || c == '’' || c == '-') continue
                return null
            }
            val k = keyOf(c) ?: return null
            if (n == 0 || out[n - 1] != k) out[n++] = k
        }
        return if (n < 2) null else out.copyOf(n)
    }

    private fun index(d: Dictionary): Index {
        val lists = HashMap<Int, IntArrayList>()
        for (id in 0 until d.size) {
            val p = path(d.word(id)) ?: continue
            lists.getOrPut(bucket(p[0], p[p.size - 1])) { IntArrayList() }.add(id)
        }
        val map = HashMap<Int, IntArray>(lists.size * 2)
        for ((k, v) in lists) map[k] = v.toArray()
        return Index(map)
    }

    private fun bucket(a: Char, b: Char) = (a.code shl 16) or b.code

    private class IntArrayList {
        var a = IntArray(16)
        var n = 0
        fun add(v: Int) {
            if (n == a.size) a = a.copyOf(n * 2)
            a[n++] = v
        }
        fun toArray() = a.copyOf(n)
    }

    /**
     * Decodes a swipe. [xs] and [ys] are the finger's points in key widths. Returns the best
     * words first; each case form of a word appears once, the lower-case one when both exist.
     */
    fun decode(xs: FloatArray, ys: FloatArray, limit: Int = 4): List<Result> {
        if (xs.size < 2) return emptyList()
        val ux = FloatArray(POINTS)
        val uy = FloatArray(POINTS)
        val userLen = resample(xs, ys, xs.size, ux, uy)
        if (userLen < 0.5f) return emptyList()
        val userShape = normalize(ux, uy)

        val starts = nearKeys(ux[0], uy[0])
        val ends = nearKeys(ux[POINTS - 1], uy[POINTS - 1])
        val best = HashMap<String, Result>()
        val px = FloatArray(MAX_PATH)
        val py = FloatArray(MAX_PATH)
        val ix = FloatArray(POINTS)
        val iy = FloatArray(POINTS)

        dicts.forEachIndexed { di, d ->
            val idx = indexes[di]
            val bonus = if (di == 0) Corrector.CURRENT_LANGUAGE_BONUS / 255f else 0f
            for (s in starts) for (e in ends) {
                val ids = idx.buckets[bucket(s, e)] ?: continue
                for (id in ids) {
                    val w = d.word(id)
                    val p = path(w) ?: continue
                    if (p.size > MAX_PATH) continue
                    var len = 0f
                    for (i in p.indices) {
                        val c = keys.center(p[i])!!
                        px[i] = c[0]
                        py[i] = c[1]
                        if (i > 0) len += hypot(px[i] - px[i - 1], py[i] - py[i - 1])
                    }
                    // A path far shorter or longer than the finger's can't be the word.
                    if (len > userLen * 2.2f + 1f || len < userLen * 0.4f - 1f) continue
                    resample(px, py, p.size, ix, iy)
                    var loc = 0f
                    for (i in 0 until POINTS) loc += hypot(ux[i] - ix[i], uy[i] - iy[i])
                    loc /= POINTS
                    if (loc > MAX_LOCATION) continue
                    val miss = keyMiss(ux, uy, px, py, p.size)
                    val shape = shapeDistance(userShape, ix, iy)
                    val score = d.frequency(id) / 255f * FREQ_WEIGHT - SHAPE_WEIGHT * shape - LOCATION_WEIGHT * loc - MISS_WEIGHT * miss + bonus
                    val key = w.lowercase()
                    val cand = Result(w, score, d.offensive(id))
                    val prev = best[key]
                    best[key] = when {
                        prev == null -> cand
                        prev.word == w -> if (score > prev.score) cand else prev
                        // Case forms of one word ("El" and "el"): the lower-case form, with the better score.
                        else -> {
                            val form = when {
                                w[0].isLowerCase() -> w
                                prev.word[0].isLowerCase() -> prev.word
                                score > prev.score -> w
                                else -> prev.word
                            }
                            Result(form, max(score, prev.score), cand.offensive || prev.offensive)
                        }
                    }
                }
            }
        }
        return best.values.sortedByDescending { it.score }.take(limit)
    }

    /** Letters whose key centre is within [NEAR_END] key widths of a point. */
    private fun nearKeys(x: Float, y: Float): List<Char> {
        val out = ArrayList<Char>()
        for (c in keys.letters) {
            val p = keys.center(c) ?: continue
            if (hypot(p[0] - x, (p[1] - y)) <= NEAR_END) out += c
        }
        if (out.isEmpty()) {
            keys.letters.minByOrNull { val p = keys.center(it)!!; hypot(p[0] - x, p[1] - y) }?.let { out += it }
        }
        return out
    }

    /** The largest distance from one of the word's keys to the finger's path: a key the swipe never went near. */
    private fun keyMiss(ux: FloatArray, uy: FloatArray, px: FloatArray, py: FloatArray, n: Int): Float {
        var worst = 0f
        for (k in 0 until n) {
            var m = Float.MAX_VALUE
            for (i in 0 until POINTS) {
                val dx = ux[i] - px[k]
                val dy = uy[i] - py[k]
                val dd = dx * dx + dy * dy
                if (dd < m) m = dd
            }
            val d = sqrt(m)
            if (d > worst) worst = d
        }
        return worst
    }

    private class Shape(val x: FloatArray, val y: FloatArray)

    /** Centred on its middle and scaled so its larger side is 1. */
    private fun normalize(x: FloatArray, y: FloatArray): Shape {
        val nx = x.copyOf()
        val ny = y.copyOf()
        normalizeInPlace(nx, ny)
        return Shape(nx, ny)
    }

    private fun normalizeInPlace(x: FloatArray, y: FloatArray) {
        var cx = 0f
        var cy = 0f
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in x.indices) {
            cx += x[i]
            cy += y[i]
            minX = minOf(minX, x[i]); maxX = maxOf(maxX, x[i])
            minY = minOf(minY, y[i]); maxY = maxOf(maxY, y[i])
        }
        cx /= x.size
        cy /= y.size
        val s = max(max(maxX - minX, maxY - minY), 0.01f)
        for (i in x.indices) {
            x[i] = (x[i] - cx) / s
            y[i] = (y[i] - cy) / s
        }
    }

    private val sx = FloatArray(POINTS)
    private val sy = FloatArray(POINTS)

    @Synchronized
    private fun shapeDistance(user: Shape, ix: FloatArray, iy: FloatArray): Float {
        ix.copyInto(sx)
        iy.copyInto(sy)
        normalizeInPlace(sx, sy)
        var sum = 0f
        for (i in 0 until POINTS) sum += hypot(user.x[i] - sx[i], user.y[i] - sy[i])
        return sum / POINTS
    }

    companion object {
        const val POINTS = 64
        const val MAX_PATH = 48

        /** Start and end keys this many key widths from the finger make the shortlist. */
        const val NEAR_END = 1.15f

        /** A word whose path strays further than this on average, in key widths, is dropped early. */
        const val MAX_LOCATION = 1.6f

        const val FREQ_WEIGHT = 1.0f
        const val SHAPE_WEIGHT = 1.6f
        const val LOCATION_WEIGHT = 1.3f
        const val MISS_WEIGHT = 0.9f

        /**
         * Resamples the first [n] points of a polyline to [POINTS] points evenly spaced along its
         * length, into [ox] and [oy]. Returns the length. A single point repeats.
         */
        fun resample(x: FloatArray, y: FloatArray, n: Int, ox: FloatArray, oy: FloatArray): Float {
            var total = 0f
            for (i in 1 until n) total += hypot(x[i] - x[i - 1], y[i] - y[i - 1])
            if (total <= 0f || n < 2) {
                ox.fill(x[0])
                oy.fill(y[0])
                return 0f
            }
            val step = total / (POINTS - 1)
            ox[0] = x[0]
            oy[0] = y[0]
            var k = 1
            var acc = 0f
            var i = 1
            var px = x[0]
            var py = y[0]
            while (i < n && k < POINTS) {
                val seg = hypot(x[i] - px, y[i] - py)
                if (acc + seg >= step && seg > 0f) {
                    val t = (step - acc) / seg
                    val nx = px + t * (x[i] - px)
                    val ny = py + t * (y[i] - py)
                    ox[k] = nx
                    oy[k] = ny
                    k++
                    px = nx
                    py = ny
                    acc = 0f
                } else {
                    acc += seg
                    px = x[i]
                    py = y[i]
                    i++
                }
            }
            while (k < POINTS) {
                ox[k] = x[n - 1]
                oy[k] = y[n - 1]
                k++
            }
            return total
        }
    }
}
