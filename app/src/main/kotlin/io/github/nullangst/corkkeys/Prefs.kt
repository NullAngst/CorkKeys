/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Where settings are read from and written to. A plain map in the tests. */
interface KeyValues {
    fun getBoolean(key: String, def: Boolean): Boolean
    fun getString(key: String, def: String): String
    fun getFloat(key: String, def: Float): Float
    fun put(key: String, value: Any)
}

class MapKeyValues : KeyValues {
    val map = HashMap<String, Any>()
    override fun getBoolean(key: String, def: Boolean) = map[key] as? Boolean ?: def
    override fun getString(key: String, def: String) = map[key] as? String ?: def
    override fun getFloat(key: String, def: Float) = map[key] as? Float ?: def
    override fun put(key: String, value: Any) {
        map[key] = value
    }
}

private class SharedKeyValues(private val sp: SharedPreferences) : KeyValues {
    override fun getBoolean(key: String, def: Boolean) = sp.getBoolean(key, def)
    override fun getString(key: String, def: String) = sp.getString(key, def) ?: def
    override fun getFloat(key: String, def: Float) = sp.getFloat(key, def)
    override fun put(key: String, value: Any) {
        val e = sp.edit()
        when (value) {
            is Boolean -> e.putBoolean(key, value)
            is Float -> e.putFloat(key, value)
            else -> e.putString(key, value.toString())
        }
        e.apply()
    }
}

/**
 * Every setting with its default. A new install is a plain keyboard: suggestions, autocorrect,
 * swipe, learned words, the swear-word switch and the number row are off; vibration and key
 * bubbles are on.
 *
 * Settings live in device-protected storage, so the keyboard has them on the lock screen right
 * after a reboot, before the phone has been unlocked once.
 */
class Prefs(val kv: KeyValues, systemLanguage: String = "en") {
    private fun bool(key: String, def: Boolean) = object : ReadWriteProperty<Prefs, Boolean> {
        override fun getValue(thisRef: Prefs, property: KProperty<*>) = kv.getBoolean(key, def)
        override fun setValue(thisRef: Prefs, property: KProperty<*>, value: Boolean) = kv.put(key, value)
    }

    private fun str(key: String, def: String) = object : ReadWriteProperty<Prefs, String> {
        override fun getValue(thisRef: Prefs, property: KProperty<*>) = kv.getString(key, def)
        override fun setValue(thisRef: Prefs, property: KProperty<*>, value: String) = kv.put(key, value)
    }

    private fun float(key: String, def: Float) = object : ReadWriteProperty<Prefs, Float> {
        override fun getValue(thisRef: Prefs, property: KProperty<*>) = kv.getFloat(key, def)
        override fun setValue(thisRef: Prefs, property: KProperty<*>, value: Float) = kv.put(key, value)
    }

    private val firstLanguage = if (systemLanguage in Languages.ALL) systemLanguage else "en"

    // Typing
    var suggestions by bool("suggestions", false)
    var autocorrect by bool("autocorrect", false)
    var swearWords by bool("swear_words", false)
    var inNoSuggestFields by bool("in_no_suggest_fields", false)
    var swipe by bool("swipe", false)
    var doubleSpace by bool("double_space", true)
    var autoCaps by bool("auto_caps", true)

    // Privacy page
    var learn by bool("learn", false)
    var incognito by bool("incognito", false)

    // Languages: a comma-separated list of codes, and the one in use.
    var languagesRaw by str("languages", firstLanguage)
    var current by str("current", firstLanguage)
    var twoAtOnce by bool("two_at_once", true)

    // Keys and feel
    var numberRow by bool("number_row", false)
    var vibrate by bool("vibrate", true)
    var bubbles by bool("bubbles", true)
    var bordered by bool("bordered", true)
    var heightRaw by str("height", KeyHeight.NORMAL.name)
    var theme by str("theme", Themes.DEFAULT)

    // Modes
    var modeRaw by str("mode", Mode.DOCKED.name)
    var floatX by float("float_x", 0.5f)
    var floatY by float("float_y", 0.5f)
    var lastOneHanded by str("last_one_handed", Mode.ONE_RIGHT.name)

    /** The keyboard picked for the mic button, as an input method id. Empty: none, no mic. */
    var voiceIme by str("voice_ime", "")

    /** Row height in dp when the height is set to custom. */
    var customRowDp by float("custom_row_dp", KeyHeight.TALLER.dp)

    /** Whether the height is the custom one rather than a preset. */
    val customHeight: Boolean get() = heightRaw == CUSTOM

    /** The row height to draw, in dp: the preset's, or the custom one. */
    val rowDp: Float get() = if (customHeight) customRowDp.coerceIn(Geometry.MIN_ROW_DP, Geometry.MAX_ROW_DP) else height.dp

    var height: KeyHeight
        get() = KeyHeight.entries.firstOrNull { it.name == heightRaw } ?: KeyHeight.NORMAL
        set(v) {
            heightRaw = v.name
        }

    var mode: Mode
        get() = Mode.entries.firstOrNull { it.name == modeRaw } ?: Mode.DOCKED
        set(v) {
            modeRaw = v.name
        }

    /** Languages that are on, in the fixed order. Never empty. */
    var languages: List<String>
        get() = Languages.ALL.filter { it in languagesRaw.split(',') }.ifEmpty { listOf(firstLanguage) }
        set(v) {
            languagesRaw = Languages.ALL.filter { it in v }.ifEmpty { listOf(firstLanguage) }.joinToString(",")
            if (current !in languages) current = languages.first()
        }

    /** The language in use, always one that is on. */
    val language: String get() = current.takeIf { it in languages } ?: languages.first()

    companion object {
        const val CUSTOM = "CUSTOM"
        private const val FILE = "settings"

        fun open(ctx: Context): Prefs {
            val dp = ctx.createDeviceProtectedStorageContext()
            return Prefs(SharedKeyValues(dp.getSharedPreferences(FILE, Context.MODE_PRIVATE)), Locale.getDefault().language)
        }
    }
}
