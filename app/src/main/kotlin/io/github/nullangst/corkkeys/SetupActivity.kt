/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * First-run screen: enable the keyboard in Android's settings, then switch to it. Each step
 * shows a tick once it's done. The screen scrolls and keeps clear of the system bars, so its
 * text is never cut off.
 */
class SetupActivity : Activity() {
    private lateinit var pal: Palette
    private lateinit var scroll: ScrollView
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(list) }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    // The input method picker is a dialog over this screen: focus comes back when it closes.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) build()
    }

    private fun add(v: View) = list.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

    private fun spacer(dp: Float) = list.addView(View(this), LinearLayout.LayoutParams(1, Ui.dp(this, dp)))

    private fun build() {
        pal = Themes.palette(this, Prefs.open(this).theme)
        Ui.edgeToEdge(this, scroll, pal)
        scroll.setBackgroundColor(pal.bg)
        list.removeAllViews()
        val enabled = isEnabled(this)
        val current = isCurrent(this)

        add(Ui.title(this, pal, getString(R.string.setup_title)))
        add(Ui.note(this, pal, getString(R.string.setup_intro)))

        add(Ui.header(this, pal, getString(R.string.setup_step1)))
        add(Ui.note(this, pal, getString(if (enabled) R.string.setup_step1_done else R.string.setup_step1_sub)))
        add(padded(Ui.button(this, pal, getString(R.string.setup_enable)) {
            try {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            } catch (_: Exception) {
            }
        }))

        add(Ui.header(this, pal, getString(R.string.setup_step2)))
        add(Ui.note(this, pal, getString(if (current) R.string.setup_step2_done else R.string.setup_step2_sub)))
        add(padded(Ui.button(this, pal, getString(R.string.setup_switch)) {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }.apply {
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.5f
        }))

        if (enabled && current) {
            add(Ui.header(this, pal, getString(R.string.setup_try)))
            add(padded(EditText(this).apply {
                hint = getString(R.string.setup_try_hint)
                setTextColor(pal.text)
                setHintTextColor(pal.textDim)
            }))
            add(padded(Ui.button(this, pal, getString(R.string.setup_open_settings)) {
                startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_FROM_SETUP, true))
                finish()
            }))
        }
        spacer(24f)
    }

    private fun padded(v: View) = LinearLayout(this).apply {
        setPadding(Ui.dp(context, 20f), Ui.dp(context, 4f), Ui.dp(context, 20f), Ui.dp(context, 8f))
        addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    companion object {
        fun isEnabled(ctx: Context): Boolean = try {
            ctx.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.any { it.packageName == ctx.packageName } == true
        } catch (_: Exception) {
            false
        }

        fun isCurrent(ctx: Context): Boolean = try {
            val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
            id.substringBefore('/') == ctx.packageName
        } catch (_: Exception) {
            false
        }
    }
}
