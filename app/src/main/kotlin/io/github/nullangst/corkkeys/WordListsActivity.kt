/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Learned words and removed words, each listed in full. Tap a word to delete it from the list:
 * a learned word is forgotten, a removed word can be suggested again.
 */
class WordListsActivity : Activity() {
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

    private fun str(id: Int) = getString(id)

    private fun add(v: View) = list.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

    private fun build() {
        pal = Themes.palette(this, Prefs.open(this).theme)
        Ui.edgeToEdge(this, scroll, pal)
        scroll.setBackgroundColor(pal.bg)
        list.removeAllViews()
        val learned = LearnedWords.get(this)
        val removed = RemovedWords.get(this)

        add(Ui.title(this, pal, str(R.string.words_title)))

        add(Ui.header(this, pal, str(R.string.words_learned)))
        val l = learned.all().sortedWith(String.CASE_INSENSITIVE_ORDER)
        if (l.isEmpty()) add(Ui.note(this, pal, str(R.string.words_learned_none)))
        for (w in l) add(Ui.row(this, pal, w, str(R.string.words_tap_forget)) { confirm(w) { learned.remove(w) } })
        if (l.isNotEmpty()) add(Ui.row(this, pal, str(R.string.words_forget_all), null) { confirmAll { learned.clear() } })

        add(Ui.header(this, pal, str(R.string.words_removed)))
        val r = removed.all().sorted()
        if (r.isEmpty()) add(Ui.note(this, pal, str(R.string.words_removed_none)))
        for (w in r) add(Ui.row(this, pal, w, str(R.string.words_tap_allow)) { confirm(w) { removed.remove(w) } })
        if (r.isNotEmpty()) add(Ui.row(this, pal, str(R.string.words_allow_all), null) { confirmAll { removed.clear() } })

        list.addView(View(this), LinearLayout.LayoutParams(1, Ui.dp(this, 24f)))
    }

    private fun confirm(word: String, action: () -> Unit) {
        AlertDialog.Builder(this, Themes.dialogTheme(pal))
            .setTitle(word)
            .setPositiveButton(R.string.words_delete) { _, _ ->
                action()
                build()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmAll(action: () -> Unit) {
        AlertDialog.Builder(this, Themes.dialogTheme(pal))
            .setMessage(R.string.words_all_sure)
            .setPositiveButton(R.string.words_delete) { _, _ ->
                action()
                build()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
