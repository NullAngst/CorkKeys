/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider

/**
 * TalkBack support. Every key is a virtual node with its own description and bounds, so
 * TalkBack reads keys like buttons. With explore-by-touch on, the finger slides to find a key
 * (each key is announced as it is reached) and lifting types it, the way the stock Android
 * keyboard works. Double-tap on a focused key types it too.
 */
class KeyAccessibility(private val view: KeyboardView, private val host: Host) : AccessibilityNodeProvider() {
    interface Host {
        fun describe(k: PlacedKey): String
        fun typeFromAccessibility(k: PlacedKey)
    }

    private var hovered = NONE
    private var focused = NONE
    private val loc = IntArray(2)

    private fun keys(): List<PlacedKey> = view.plan?.keys.orEmpty()

    fun invalidateAll() {
        hovered = NONE
        focused = NONE
        view.parent?.let {
            @Suppress("DEPRECATION")
            val e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            e.packageName = view.context.packageName
            e.setSource(view)
            try {
                it.requestSendAccessibilityEvent(view, e)
            } catch (_: Exception) {
            }
        }
    }

    override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
        if (virtualViewId == View.NO_ID || virtualViewId == AccessibilityNodeProvider.HOST_VIEW_ID) {
            @Suppress("DEPRECATION")
            val info = AccessibilityNodeInfo.obtain(view)
            view.onInitializeAccessibilityNodeInfo(info)
            for (i in keys().indices) info.addChild(view, i)
            return info
        }
        val k = keys().getOrNull(virtualViewId) ?: return null
        @Suppress("DEPRECATION")
        val info = AccessibilityNodeInfo.obtain(view, virtualViewId)
        info.packageName = view.context.packageName
        info.className = android.widget.Button::class.java.name
        info.contentDescription = host.describe(k)
        info.setParent(view)
        val b = k.box
        val r = Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
        @Suppress("DEPRECATION")
        info.setBoundsInParent(r)
        view.getLocationOnScreen(loc)
        r.offset(loc[0], loc[1])
        info.setBoundsInScreen(r)
        info.isEnabled = true
        info.isVisibleToUser = true
        info.isClickable = true
        info.isFocusable = true
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        if (focused == virtualViewId) {
            info.isAccessibilityFocused = true
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
        } else {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)
        }
        return info
    }

    override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        if (virtualViewId == View.NO_ID || virtualViewId == AccessibilityNodeProvider.HOST_VIEW_ID) return view.performAccessibilityAction(action, arguments)
        val k = keys().getOrNull(virtualViewId) ?: return false
        return when (action) {
            AccessibilityNodeInfo.ACTION_CLICK -> {
                host.typeFromAccessibility(k)
                send(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                true
            }
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                if (focused == virtualViewId) return false
                focused = virtualViewId
                send(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                true
            }
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                if (focused != virtualViewId) return false
                focused = NONE
                send(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                true
            }
            else -> false
        }
    }

    /** Explore-by-touch: hovering announces keys, lifting inside the keyboard types the last one. */
    fun onHover(e: MotionEvent): Boolean {
        val plan = view.plan ?: return false
        val k = plan.find(e.x, e.y)
        val i = if (k == null) NONE else keys().indexOf(k)
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> if (i != hovered) {
                if (hovered != NONE) send(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
                hovered = i
                if (i != NONE) {
                    send(i, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                    performAction(i, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
                }
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                val last = hovered
                hovered = NONE
                if (last != NONE) {
                    send(last, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
                    // An exit inside the view is the finger lifting; outside it, the finger left.
                    val inside = e.x >= 0 && e.y >= 0 && e.x < view.width && e.y < view.height
                    if (inside) keys().getOrNull(last)?.let { host.typeFromAccessibility(it) }
                }
            }
        }
        return true
    }

    private fun send(id: Int, type: Int) {
        val k = keys().getOrNull(id) ?: return
        val parent = view.parent ?: return
        @Suppress("DEPRECATION")
        val e = AccessibilityEvent.obtain(type)
        e.packageName = view.context.packageName
        e.className = android.widget.Button::class.java.name
        e.contentDescription = host.describe(k)
        e.setSource(view, id)
        e.isEnabled = true
        try {
            parent.requestSendAccessibilityEvent(view, e)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val NONE = -1
    }
}
