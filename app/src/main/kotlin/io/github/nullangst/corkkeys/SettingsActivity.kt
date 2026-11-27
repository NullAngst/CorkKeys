/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * The one settings screen: languages, theme and every switch, plus links to the Privacy page
 * for anything that stores or reads data. Opens from the launcher, from the keyboard's tools
 * row, or with a long-press on the comma key. Changes apply the next time the keyboard opens.
 */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var pal: Palette
    private lateinit var scroll: ScrollView
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.open(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(list) }
        setContentView(scroll)
        // First launch, or the keyboard is not set up yet: the setup screen comes first.
        val fromSetup = intent?.getBooleanExtra(EXTRA_FROM_SETUP, false) == true
        if (savedInstanceState == null && !fromSetup && !(SetupActivity.isEnabled(this) && SetupActivity.isCurrent(this))) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun str(id: Int) = getString(id)

    private fun add(v: View) = list.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

    private fun switch(title: Int, value: Boolean, sub: Int? = null, enabled: Boolean = true, set: (Boolean) -> Unit) =
        add(Ui.switchRow(this, pal, str(title), value, sub?.let { str(it) }, enabled) { set(it); build() })

    private fun build() {
        pal = Themes.palette(this, prefs.theme)
        Ui.edgeToEdge(this, scroll, pal)
        scroll.setBackgroundColor(pal.bg)
        val y = scroll.scrollY
        list.removeAllViews()

        add(Ui.title(this, pal, str(R.string.settings_title)))
        if (!(SetupActivity.isEnabled(this) && SetupActivity.isCurrent(this))) {
            add(Ui.row(this, pal, str(R.string.settings_setup), str(R.string.settings_setup_sub)) {
                startActivity(Intent(this, SetupActivity::class.java))
            })
        }

        // ---- Languages ----
        add(Ui.header(this, pal, str(R.string.settings_languages)))
        val on = prefs.languages
        for (lang in Languages.ALL) {
            val isOn = lang in on
            // At least one language stays on.
            add(Ui.switchRow(this, pal, Languages.name(this, lang), isOn, null, !(isOn && on.size == 1)) { now ->
                prefs.languages = if (now) on + lang else on - lang
                build()
            })
        }
        switch(R.string.settings_two_at_once, prefs.twoAtOnce, R.string.settings_two_at_once_sub, prefs.suggestions && on.size > 1) {
            prefs.twoAtOnce = it
        }
        add(Ui.note(this, pal, str(R.string.settings_languages_note)))

        // ---- Keys ----
        add(Ui.header(this, pal, str(R.string.settings_keys)))
        switch(R.string.settings_number_row, prefs.numberRow) { prefs.numberRow = it }
        val heights = KeyHeight.entries
        val heightNames = listOf(str(R.string.height_short), str(R.string.height_normal), str(R.string.height_tall))
        add(Ui.row(this, pal, str(R.string.settings_height), heightNames[heights.indexOf(prefs.height)]) {
            Ui.choose(this, pal, str(R.string.settings_height), heightNames, heights.indexOf(prefs.height)) {
                prefs.height = heights[it]
                build()
            }
        })
        switch(R.string.settings_bordered, prefs.bordered, R.string.settings_bordered_sub) { prefs.bordered = it }
        switch(R.string.settings_bubbles, prefs.bubbles, R.string.settings_bubbles_sub) { prefs.bubbles = it }
        switch(R.string.settings_vibrate, prefs.vibrate, R.string.settings_vibrate_sub) { prefs.vibrate = it }
        add(Ui.row(this, pal, str(R.string.settings_theme), Themes.name(this, prefs.theme)) {
            val names = Themes.ids.map { Themes.name(this, it) }
            Ui.choose(this, pal, str(R.string.settings_theme), names, Themes.ids.indexOf(prefs.theme).coerceAtLeast(0)) {
                prefs.theme = Themes.ids[it]
                build()
            }
        })

        // ---- Typing ----
        add(Ui.header(this, pal, str(R.string.settings_typing)))
        switch(R.string.settings_suggestions, prefs.suggestions, R.string.settings_suggestions_sub) { prefs.suggestions = it }
        switch(R.string.settings_autocorrect, prefs.autocorrect, R.string.settings_autocorrect_sub, prefs.suggestions) { prefs.autocorrect = it }
        switch(R.string.settings_swear, prefs.swearWords, R.string.settings_swear_sub, prefs.suggestions) { prefs.swearWords = it }
        switch(R.string.settings_no_suggest_fields, prefs.inNoSuggestFields, R.string.settings_no_suggest_fields_sub, prefs.suggestions) {
            prefs.inNoSuggestFields = it
        }
        add(Ui.row(this, pal, str(R.string.words_title), str(R.string.settings_words_sub)) {
            startActivity(Intent(this, WordListsActivity::class.java))
        })
        switch(R.string.settings_double_space, prefs.doubleSpace, R.string.settings_double_space_sub) { prefs.doubleSpace = it }
        switch(R.string.settings_auto_caps, prefs.autoCaps, R.string.settings_auto_caps_sub) { prefs.autoCaps = it }

        // ---- Privacy ----
        add(Ui.header(this, pal, str(R.string.settings_privacy)))
        add(Ui.row(this, pal, str(R.string.privacy_title), str(R.string.settings_privacy_sub)) {
            startActivity(Intent(this, PrivacyActivity::class.java))
        })

        // ---- About ----
        add(Ui.header(this, pal, str(R.string.settings_about)))
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            null
        }
        add(Ui.row(this, pal, getString(R.string.app_name) + " " + version.orEmpty(), str(R.string.about_licence), null))
        add(Ui.row(this, pal, str(R.string.about_source), SOURCE_URL) { open(SOURCE_URL) })
        add(Ui.row(this, pal, str(R.string.about_words), str(R.string.about_words_sub)) { open(LICENCE_URL) })
        add(Ui.note(this, pal, str(R.string.settings_spell_checker_note)))
        add(Ui.note(this, pal, str(R.string.about_openboard)))
        list.addView(View(this), LinearLayout.LayoutParams(1, Ui.dp(this, 24f)))
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    companion object {
        const val EXTRA_FROM_SETUP = "from_setup"
        const val SOURCE_URL = "https://github.com/NullAngst/CorkKeys"
        const val LICENCE_URL = "https://creativecommons.org/licenses/by/4.0/"
    }
}
