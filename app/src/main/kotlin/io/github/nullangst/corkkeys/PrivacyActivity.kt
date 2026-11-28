/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * The Privacy page: every switch for something that stores what you type, off on a new install,
 * each with a line saying why, and the plain facts about what the keyboard can and can't do.
 * The permission list is read from the installed app itself, so it can't drift from the truth.
 */
class PrivacyActivity : Activity() {
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
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun str(id: Int) = getString(id)

    private fun add(v: View) = list.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

    private fun build() {
        pal = Themes.palette(this, prefs.theme)
        Ui.edgeToEdge(this, scroll, pal)
        scroll.setBackgroundColor(pal.bg)
        list.removeAllViews()

        add(Ui.title(this, pal, str(R.string.privacy_title)))
        add(Ui.note(this, pal, str(R.string.privacy_intro)))

        add(Ui.header(this, pal, str(R.string.privacy_switches)))
        add(Ui.switchRow(this, pal, str(R.string.privacy_learn), prefs.learn, str(R.string.privacy_learn_sub)) { on ->
            prefs.learn = on
            // Off means gone: the learned words are deleted, not just ignored.
            if (!on) LearnedWords.get(this).clear()
            build()
        })
        add(Ui.row(this, pal, str(R.string.words_title), str(R.string.settings_words_sub)) {
            startActivity(Intent(this, WordListsActivity::class.java))
        })
        add(Ui.switchRow(this, pal, str(R.string.privacy_incognito), prefs.incognito, str(R.string.privacy_incognito_sub)) {
            prefs.incognito = it
            build()
        })
        add(Ui.switchRow(this, pal, str(R.string.privacy_clip), prefs.clipHistory, str(R.string.privacy_clip_sub)) { on ->
            prefs.clipHistory = on
            val h = ClipboardHistory.get(this)
            // Off means gone: memory, the saved file and pinned clips.
            if (on) h.sync() else h.wipe()
            build()
        })
        if (prefs.clipHistory) {
            val names = KEEP_CHOICES.map { keepName(it) }
            val current = KEEP_CHOICES.indexOf(prefs.clipKeep).coerceAtLeast(0)
            add(Ui.row(this, pal, str(R.string.privacy_clip_keep), keepName(prefs.clipKeep)) {
                Ui.choose(this, pal, str(R.string.privacy_clip_keep), names, current) {
                    prefs.clipKeep = KEEP_CHOICES[it]
                    ClipboardHistory.get(this).save()
                    build()
                }
            })
            add(Ui.note(this, pal, str(R.string.privacy_clip_keep_note)))
        }

        add(Ui.header(this, pal, str(R.string.privacy_facts)))
        for (id in listOf(
            R.string.privacy_fact_network,
            R.string.privacy_fact_stored,
            R.string.privacy_fact_removed,
            R.string.privacy_fact_spell,
            R.string.privacy_fact_passwords,
            R.string.privacy_fact_clipboard,
            R.string.privacy_fact_backup,
            R.string.privacy_fact_logs,
        )) add(Ui.note(this, pal, "• " + str(id)))

        add(Ui.header(this, pal, str(R.string.privacy_permissions)))
        val perms = requested()
        add(Ui.note(this, pal, if (perms.isEmpty()) str(R.string.privacy_permissions_none) else perms.joinToString("\n")))

        add(Ui.row(this, pal, str(R.string.privacy_read_more), PRIVACY_URL) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
            } catch (_: Exception) {
            }
        })
        list.addView(View(this), LinearLayout.LayoutParams(1, Ui.dp(this, 24f)))
    }

    private fun keepName(n: Int) = if (n == 0) str(R.string.privacy_clip_keep_none) else getString(R.string.privacy_clip_keep_n, n)

    /** Every permission in the installed app's manifest. There should be none. */
    private fun requested(): List<String> = try {
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.toList().orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        val KEEP_CHOICES = listOf(0, 1, 3, 5, 10, 20, Prefs.CLIP_KEEP_MAX)
        const val PRIVACY_URL = "https://github.com/NullAngst/CorkKeys/blob/main/PRIVACY.md"
    }
}
