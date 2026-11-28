/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The emoji board. It takes the keys' place, like the editing panel: one long scroll of every
 * emoji, recently used first, with a tab for each group along the bottom between ABC and
 * backspace. Tap an emoji to type it. Long-press one that comes in skin tones to pick a tone;
 * that tone is what its cell shows from then on.
 */
@SuppressLint("ViewConstructor")
class EmojiPanel(ctx: Context, private val host: Host) : LinearLayout(ctx) {
    interface Host {
        fun emojiPicked(text: String)
        fun emojiBackspace()
        fun emojiClose()
        fun buttonFeedback(v: View)
    }

    private val d = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false }
    private val grid = Grid(ctx)
    private val tabs = LinearLayout(ctx).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val tabViews = ArrayList<TextView>()
    private val abc = TextView(ctx)
    private val back = ImageView(ctx)
    private var pal: Palette = Themes.light
    private var recents = EmojiRecents()

    init {
        orientation = VERTICAL
        scroll.addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            grid.scrolled(y, scroll.height)
            highlightTab(grid.sectionAt(y))
        }
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(ctx).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        abc.apply {
            text = context.getString(R.string.emoji_abc)
            textSize = 14f
            gravity = Gravity.CENTER
            contentDescription = context.getString(R.string.cd_letters)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                host.buttonFeedback(it)
                host.emojiClose()
            }
        }
        back.apply {
            setImageResource(R.drawable.ic_backspace)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = context.getString(R.string.cd_backspace)
            isClickable = true
            isFocusable = true
            setOnClickListener { host.emojiBackspace() }
        }
        repeatOnHold(back)
        val h = (BAR_DP * d).toInt()
        val side = (52 * d).toInt()
        bar.addView(abc, LayoutParams(side, h))
        bar.addView(tabs, LayoutParams(0, h, 1f))
        bar.addView(back, LayoutParams(side, h))
        addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
    }

    /** Backspace repeats while held, like the key on the keyboard. A tap deletes one. */
    @SuppressLint("ClickableViewAccessibility")
    private fun repeatOnHold(v: View) {
        var repeating = false
        val tick = object : Runnable {
            override fun run() {
                repeating = true
                host.emojiBackspace()
                handler.postDelayed(this, REPEAT_MS)
            }
        }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    repeating = false
                    host.buttonFeedback(view)
                    view.isPressed = true
                    handler.postDelayed(tick, HOLD_MS)
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(tick)
                    view.isPressed = false
                    if (!repeating) host.emojiBackspace()
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(tick)
                    view.isPressed = false
                }
            }
            true
        }
    }

    fun applyPalette(p: Palette) {
        pal = p
        setBackgroundColor(p.keyboardBg)
        abc.setTextColor(p.text)
        back.imageTintList = ColorStateList.valueOf(p.text)
        grid.invalidate()
        highlightTab(grid.sectionAt(scroll.scrollY))
    }

    /** Fills the board. Recently used emoji go first, in their own row of the scroll. */
    fun show(groups: List<EmojiGroup>, r: EmojiRecents) {
        recents = r
        val sections = ArrayList<Section>()
        val byText = HashMap<String, EmojiEntry>()
        for (g in groups) for (e in g.entries) {
            byText[e.emoji] = e
            for (t in e.tones) byText[t] = e
        }
        sections += Section(RECENT, r.list.map { Item(it, byText[it]) })
        for (g in groups) sections += Section(g.id, g.entries.map { Item(r.toned(it.emoji), it) })
        grid.sections = sections
        tabs.removeAllViews()
        tabViews.clear()
        sections.forEachIndexed { i, s ->
            val tv = TextView(context).apply {
                text = if (s.id == RECENT) RECENT_ICON else EmojiData.icons[s.id] ?: s.items.firstOrNull()?.text.orEmpty()
                textSize = 16f
                gravity = Gravity.CENTER
                contentDescription = context.getString(titleOf(s.id))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    host.buttonFeedback(it)
                    scroll.scrollTo(0, grid.topOf(i))
                }
            }
            tabViews += tv
            tabs.addView(tv, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        grid.closeTones()
        grid.requestLayout()
        // Open where you left off last time only if there are recents; otherwise at the start of smileys.
        scroll.post {
            val start = if (r.list.isEmpty()) grid.topOf(1) else 0
            scroll.scrollTo(0, start)
            grid.scrolled(start, scroll.height)
            highlightTab(grid.sectionAt(start))
        }
    }

    private fun highlightTab(i: Int) {
        tabViews.forEachIndexed { j, tv ->
            tv.alpha = if (j == i) 1f else 0.45f
            tv.isSelected = j == i
        }
    }

    private fun titleOf(id: String): Int = when (id) {
        RECENT -> R.string.emoji_recent
        "smileys" -> R.string.emoji_smileys
        "people" -> R.string.emoji_people
        "animals" -> R.string.emoji_animals
        "food" -> R.string.emoji_food
        "travel" -> R.string.emoji_travel
        "activities" -> R.string.emoji_activities
        "objects" -> R.string.emoji_objects
        "symbols" -> R.string.emoji_symbols
        else -> R.string.emoji_flags
    }

    private fun pick(item: Item) {
        val e = item.entry
        val text = item.text
        recents.used(text)
        if (e != null && e.tones.isNotEmpty()) recents.pickTone(e.emoji, text)
        host.emojiPicked(text)
    }

    private class Item(val text: String, val entry: EmojiEntry?)

    private class Section(val id: String, val items: List<Item>) {
        var top = 0f
        var gridTop = 0f
        var rows = 0
    }

    /** The cells, drawn in one view. Only the rows on screen are drawn. */
    private inner class Grid(ctx: Context) : View(ctx) {
        var sections: List<Section> = emptyList()
        private var cols = 8
        private var cell = 0f
        private val header = (HEADER_DP * d)
        private var viewTop = 0
        private var viewHeight = 0
        private val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)

        /** The open skin tone picker: which item, and where its row of tones is drawn. */
        private var toneItem: Item? = null
        private var toneOptions: List<String> = emptyList()
        private val toneFrame = RectF()

        private val access = Access()

        private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (toneItem != null) {
                    val t = toneAt(e.x, e.y)
                    if (t != null) pickTone(t)
                    closeTones()
                    return true
                }
                val item = itemAt(e.x, e.y) ?: return false
                host.buttonFeedback(this@Grid)
                pick(item)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val (item, box) = itemBoxAt(e.x, e.y) ?: return
                if (item.entry == null || item.entry.tones.isEmpty()) return
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                openTones(item, box)
            }
        })

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        fun scrolled(y: Int, h: Int) {
            viewTop = y
            viewHeight = h
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            cols = (w / (CELL_DP * d)).toInt().coerceAtLeast(5)
            cell = w.toFloat() / cols
            var y = 0f
            for (s in sections) {
                s.top = y
                s.gridTop = y + header
                s.rows = (s.items.size + cols - 1) / cols
                // An empty recents section still gets a row, for the hint.
                y = s.gridTop + cell * s.rows.coerceAtLeast(1)
            }
            setMeasuredDimension(w, (y + cell * 0.25f).toInt())
        }

        fun topOf(i: Int): Int = sections.getOrNull(i)?.top?.toInt() ?: 0

        fun sectionAt(y: Int): Int {
            var found = 0
            sections.forEachIndexed { i, s -> if (s.top <= y + header) found = i }
            return found
        }

        private fun itemBoxAt(x: Float, y: Float): Pair<Item, RectF>? {
            for (s in sections) {
                if (y < s.gridTop || y >= s.gridTop + s.rows * cell) continue
                val row = ((y - s.gridTop) / cell).toInt()
                val col = (x / cell).toInt().coerceIn(0, cols - 1)
                val i = row * cols + col
                val item = s.items.getOrNull(i) ?: return null
                return item to box(s, i)
            }
            return null
        }

        private fun itemAt(x: Float, y: Float): Item? = itemBoxAt(x, y)?.first

        private fun box(s: Section, i: Int): RectF {
            val l = (i % cols) * cell
            val t = s.gridTop + (i / cols) * cell
            return RectF(l, t, l + cell, t + cell)
        }

        private fun openTones(item: Item, box: RectF) {
            val e = item.entry ?: return
            toneItem = item
            toneOptions = listOf(e.emoji) + e.tones
            val w = toneOptions.size * cell
            val left = (box.centerX() - w / 2f).coerceIn(0f, (width - w).coerceAtLeast(0f))
            // Above the cell when there is room on screen, below it otherwise.
            val top = if (box.top - cell >= viewTop) box.top - cell else box.bottom
            toneFrame.set(left, top, left + w, top + cell)
            invalidate()
            access.changed()
            announceForAccessibility(context.getString(R.string.emoji_tones_open))
        }

        fun closeTones() {
            if (toneItem == null) return
            toneItem = null
            toneOptions = emptyList()
            invalidate()
            access.changed()
        }

        private fun toneAt(x: Float, y: Float): String? {
            if (!toneFrame.contains(x, y)) return null
            return toneOptions.getOrNull(((x - toneFrame.left) / cell).toInt())
        }

        private fun pickTone(t: String) {
            val item = toneItem ?: return
            val e = item.entry ?: return
            host.buttonFeedback(this)
            recents.pickTone(e.emoji, t)
            recents.used(t)
            host.emojiPicked(t)
            // Every cell for this emoji shows the new tone now.
            sections = sections.map { s -> Section(s.id, s.items.map { if (it.entry === e && s.id != RECENT) Item(t, e) else it }) }
            requestLayout()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)

        override fun onDraw(c: Canvas) {
            emoji.textSize = cell * 0.62f
            label.textSize = 12f * d
            label.color = pal.textDim
            val top = viewTop - cell
            val bottom = viewTop + (if (viewHeight > 0) viewHeight else height) + cell
            for (s in sections) {
                if (s.gridTop + s.rows * cell < top || s.top > bottom) continue
                c.drawText(context.getString(titleOf(s.id)), 12f * d, s.top + header * 0.7f, label)
                if (s.items.isEmpty()) {
                    c.drawText(context.getString(R.string.emoji_recent_empty), 12f * d, s.gridTop + cell * 0.6f, label)
                    continue
                }
                val first = (((top - s.gridTop) / cell).toInt().coerceAtLeast(0)) * cols
                val last = ((((bottom - s.gridTop) / cell).toInt() + 1) * cols).coerceAtMost(s.items.size)
                for (i in first until last) {
                    val r = box(s, i)
                    drawEmoji(c, s.items[i].text, r)
                    if (s.items[i].entry?.tones?.isNotEmpty() == true) {
                        // A small corner mark: this one has skin tones on long-press.
                        bg.color = pal.textDim
                        c.drawCircle(r.right - cell * 0.12f, r.bottom - cell * 0.12f, 1.6f * d, bg)
                    }
                }
            }
            if (toneItem != null) {
                val radius = 10f * d
                bg.color = pal.surface
                c.drawRoundRect(toneFrame, radius, radius, bg)
                bg.color = pal.line
                bg.style = Paint.Style.STROKE
                bg.strokeWidth = d
                c.drawRoundRect(toneFrame, radius, radius, bg)
                bg.style = Paint.Style.FILL
                toneOptions.forEachIndexed { i, t ->
                    val l = toneFrame.left + i * cell
                    drawEmoji(c, t, RectF(l, toneFrame.top, l + cell, toneFrame.bottom))
                }
            }
        }

        private fun drawEmoji(c: Canvas, text: String, r: RectF) {
            c.drawText(text, r.centerX(), r.centerY() - (emoji.descent() + emoji.ascent()) / 2f, emoji)
        }

        // ---- TalkBack: each emoji is a button read by its name ----

        override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = access

        override fun onHoverEvent(event: MotionEvent): Boolean = access.onHover(event)

        private inner class Access : AccessibilityNodeProvider() {
            private var focused = NONE
            private var hovered = NONE
            private val loc = IntArray(2)

            /** Ids: tone options first while the picker is open, then every emoji in scroll order. */
            private fun all(): List<Pair<String, RectF>> {
                val out = ArrayList<Pair<String, RectF>>()
                for (s in sections) s.items.forEachIndexed { i, it -> out += it.text to box(s, i) }
                return out
            }

            private fun toneId(i: Int) = TONE_BASE + i

            private fun nameOf(text: String): String = sections.asSequence().flatMap { it.items.asSequence() }
                .firstOrNull { it.text == text }?.entry?.let { e -> if (e.emoji == text) e.name else e.name + ", " + toneName(e, text) }
                ?: text

            private fun toneName(e: EmojiEntry, t: String): String {
                val i = e.tones.indexOf(t)
                return if (i < 0) "" else context.getString(R.string.emoji_tone, i + 1)
            }

            fun changed() {
                focused = NONE
                hovered = NONE
                val p = parent ?: return
                @Suppress("DEPRECATION")
                val e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                e.packageName = context.packageName
                e.setSource(this@Grid)
                try {
                    p.requestSendAccessibilityEvent(this@Grid, e)
                } catch (_: Exception) {
                }
            }

            private fun target(id: Int): Pair<String, RectF>? {
                if (id >= TONE_BASE) {
                    val i = id - TONE_BASE
                    val t = toneOptions.getOrNull(i) ?: return null
                    val l = toneFrame.left + i * cell
                    return t to RectF(l, toneFrame.top, l + cell, toneFrame.bottom)
                }
                return all().getOrNull(id)
            }

            private fun describe(id: Int, text: String): String {
                if (id >= TONE_BASE) {
                    val e = toneItem?.entry ?: return text
                    return if (text == e.emoji) e.name + ", " + context.getString(R.string.emoji_tone_none) else e.name + ", " + toneName(e, text)
                }
                return nameOf(text)
            }

            override fun createAccessibilityNodeInfo(id: Int): AccessibilityNodeInfo? {
                if (id == View.NO_ID || id == HOST_VIEW_ID) {
                    @Suppress("DEPRECATION")
                    val info = AccessibilityNodeInfo.obtain(this@Grid)
                    onInitializeAccessibilityNodeInfo(info)
                    if (toneItem != null) toneOptions.indices.forEach { info.addChild(this@Grid, toneId(it)) }
                    else all().indices.forEach { info.addChild(this@Grid, it) }
                    return info
                }
                val (text, b) = target(id) ?: return null
                @Suppress("DEPRECATION")
                val info = AccessibilityNodeInfo.obtain(this@Grid, id)
                info.packageName = context.packageName
                info.className = android.widget.Button::class.java.name
                info.contentDescription = describe(id, text)
                info.setParent(this@Grid)
                val r = Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                @Suppress("DEPRECATION")
                info.setBoundsInParent(r)
                getLocationOnScreen(loc)
                r.offset(loc[0], loc[1])
                info.setBoundsInScreen(r)
                info.isEnabled = true
                info.isVisibleToUser = id >= TONE_BASE || (b.bottom > viewTop && b.top < viewTop + viewHeight)
                info.isClickable = true
                info.isFocusable = true
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                if (id < TONE_BASE && itemFor(id)?.entry?.tones?.isNotEmpty() == true) {
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, context.getString(R.string.emoji_tones_action)))
                }
                if (focused == id) {
                    info.isAccessibilityFocused = true
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
                } else {
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)
                }
                return info
            }

            private fun itemFor(id: Int): Item? {
                var i = id
                for (s in sections) {
                    if (i < s.items.size) return s.items[i]
                    i -= s.items.size
                }
                return null
            }

            private fun sectionAndIndex(id: Int): Pair<Section, Int>? {
                var i = id
                for (s in sections) {
                    if (i < s.items.size) return s to i
                    i -= s.items.size
                }
                return null
            }

            override fun performAction(id: Int, action: Int, arguments: Bundle?): Boolean {
                if (id == View.NO_ID || id == HOST_VIEW_ID) return performAccessibilityAction(action, arguments)
                val (text, _) = target(id) ?: return false
                return when (action) {
                    AccessibilityNodeInfo.ACTION_CLICK -> {
                        if (id >= TONE_BASE) {
                            pickTone(text)
                            closeTones()
                        } else {
                            itemFor(id)?.let { pick(it) }
                        }
                        send(id, AccessibilityEvent.TYPE_VIEW_CLICKED)
                        true
                    }
                    AccessibilityNodeInfo.ACTION_LONG_CLICK -> {
                        val (s, i) = sectionAndIndex(id) ?: return false
                        val item = s.items[i]
                        if (item.entry?.tones.isNullOrEmpty()) return false
                        openTones(item, box(s, i))
                        true
                    }
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                        if (focused == id) return false
                        focused = id
                        if (id < TONE_BASE) sectionAndIndex(id)?.let { (s, i) -> requestRectangleOnScreen(box(s, i).let { b -> Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()) }) }
                        send(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                        true
                    }
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                        if (focused != id) return false
                        focused = NONE
                        send(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                        true
                    }
                    else -> false
                }
            }

            /** Explore-by-touch: hovering reads emoji names, lifting types the last one. */
            fun onHover(e: MotionEvent): Boolean {
                val id = idAt(e.x, e.y)
                when (e.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> if (id != hovered) {
                        if (hovered != NONE) send(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
                        hovered = id
                        if (id != NONE) {
                            send(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                            performAction(id, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
                        }
                    }
                    MotionEvent.ACTION_HOVER_EXIT -> {
                        val last = hovered
                        hovered = NONE
                        if (last != NONE) {
                            send(last, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
                            val inside = e.x >= 0 && e.y >= 0 && e.x < width && e.y < height
                            if (inside) performAction(last, AccessibilityNodeInfo.ACTION_CLICK, null)
                        }
                    }
                }
                return true
            }

            private fun idAt(x: Float, y: Float): Int {
                if (toneItem != null) {
                    if (!toneFrame.contains(x, y)) return NONE
                    return toneId(((x - toneFrame.left) / cell).toInt().coerceIn(0, toneOptions.size - 1))
                }
                var base = 0
                for (s in sections) {
                    if (y >= s.gridTop && y < s.gridTop + s.rows * cell) {
                        val i = ((y - s.gridTop) / cell).toInt() * cols + (x / cell).toInt().coerceIn(0, cols - 1)
                        return if (i < s.items.size) base + i else NONE
                    }
                    base += s.items.size
                }
                return NONE
            }

            private fun send(id: Int, type: Int) {
                val (text, _) = target(id) ?: return
                val p = parent ?: return
                @Suppress("DEPRECATION")
                val e = AccessibilityEvent.obtain(type)
                e.packageName = context.packageName
                e.className = android.widget.Button::class.java.name
                e.contentDescription = describe(id, text)
                e.setSource(this@Grid, id)
                e.isEnabled = true
                try {
                    p.requestSendAccessibilityEvent(this@Grid, e)
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        private const val RECENT = "recent"
        private const val RECENT_ICON = "🕘"
        private const val CELL_DP = 44f
        private const val HEADER_DP = 26f
        private const val BAR_DP = 44f
        private const val HOLD_MS = 400L
        private const val REPEAT_MS = 60L
        private const val NONE = -1
        private const val TONE_BASE = 1_000_000
    }
}
