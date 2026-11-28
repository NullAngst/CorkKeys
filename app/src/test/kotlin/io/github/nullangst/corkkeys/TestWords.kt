/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import java.io.File

/** The shipped word lists, loaded once for all the tests that need them. */
object TestWords {
    /** Unit tests run from the app module's folder. */
    val dir = File("src/main/assets/words")

    private val cache = HashMap<String, Dictionary>()

    @Synchronized
    fun get(lang: String): Dictionary = cache.getOrPut(lang) {
        val extra = File(dir, "${lang}_extra.txt").takeIf { it.exists() }?.inputStream()
        Dictionary.read(lang, File(dir, "$lang.txt.gz").inputStream(), extra)
    }

    fun keys(lang: String) = KeyMap.of(Letters.build(Letters.data(lang), false))

    fun corrector(vararg langs: String) = Corrector(langs.map { get(it) }, keys(langs[0]))
}
