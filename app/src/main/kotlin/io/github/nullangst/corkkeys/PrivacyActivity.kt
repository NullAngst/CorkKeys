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
        add(Ui.switchRow(this, pal, str(R.string.privacy_incognito), prefs.incognito, str(R.string.privacy_incognito_sub)) {
            prefs.incognito = it
            build()
        })

        add(Ui.header(this, pal, str(R.string.privacy_facts)))
        for (id in listOf(
            R.string.privacy_fact_network,
            R.string.privacy_fact_stored,
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

    /** Every permission in the installed app's manifest. There should be none. */
    private fun requested(): List<String> = try {
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.toList().orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        const val PRIVACY_URL = "https://github.com/NullAngst/CorkKeys/blob/main/PRIVACY.md"
    }
}
