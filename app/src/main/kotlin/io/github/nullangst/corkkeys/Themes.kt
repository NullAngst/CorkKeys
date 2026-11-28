/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Build

/**
 * Colours for the keyboard and its settings screens. The palettes are Corkboard's, so the two
 * apps match: background, surface, text, dimmed text and accent from the same values, with the
 * key faces mixed from them.
 */
class Palette(
    val isDark: Boolean,
    val bg: Int,
    val surface: Int,
    val text: Int,
    val textDim: Int,
    val accent: Int,
    val onAccent: Int,
) {
    /**
     * Behind the keys. Light themes sit a shade darker than the palette's background, so white
     * keys stand out from it.
     */
    val keyboardBg: Int get() = if (isDark) bg else mix(bg, text, 0.08f)

    /** Letter keys. */
    val key: Int get() = surface

    /** Shift, backspace, ?123 and the other function keys. */
    val keyFunction: Int get() = mix(surface, keyboardBg, 0.55f)

    val keyPressed: Int get() = mix(surface, text, 0.18f)

    /** The faint line under a flat key and around popups. */
    val line: Int get() = (textDim and 0x00FFFFFF) or 0x33000000

    val accentList: ColorStateList get() = ColorStateList.valueOf(accent)

    /** A rounded card for the settings screens. */
    fun card(ctx: Context, color: Int = surface, radiusDp: Float = 20f) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * ctx.resources.displayMetrics.density
    }

    companion object {
        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}

object Themes {
    /** Material You, which falls back to Light or Dark with the system on Android 10 and 11. */
    const val DEFAULT = "material"

    val ids = listOf("material", "light", "dark", "verdigris", "kawaii_light", "kawaii_dark")

    fun name(ctx: Context, id: String): String = ctx.getString(
        when (id) {
            "light" -> R.string.theme_light
            "dark" -> R.string.theme_dark
            "verdigris" -> R.string.theme_verdigris
            "kawaii_light" -> R.string.theme_kawaii_light
            "kawaii_dark" -> R.string.theme_kawaii_dark
            else -> R.string.theme_material
        },
    )

    private fun c(v: Long) = v.toInt()

    val light = Palette(false, c(0xFFF4F5F3), c(0xFFFFFFFF), c(0xFF1B1C1C), c(0xFF5E6366), c(0xFF2F6CA8), c(0xFFFFFFFF))
    val dark = Palette(true, c(0xFF121314), c(0xFF2A2C2E), c(0xFFECEDEE), c(0xFFA3A7AB), c(0xFF8AB4F8), c(0xFF0F1A2A))
    val verdigris = Palette(true, c(0xFF0F201D), c(0xFF19332F), c(0xFFE4F1EC), c(0xFF9CBDB4), c(0xFFC27A44), c(0xFF0F201D))
    val kawaiiLight = Palette(false, c(0xFFFFF3F8), c(0xFFFFFFFF), c(0xFF4A2B3D), c(0xFF9A7488), c(0xFFE76FA3), c(0xFFFFFFFF))
    val kawaiiDark = Palette(true, c(0xFF231722), c(0xFF33222F), c(0xFFFBE4EF), c(0xFFC9A3B6), c(0xFFF7A8C8), c(0xFF231722))

    fun palette(ctx: Context, id: String): Palette = when (id) {
        "light" -> light
        "dark" -> dark
        "verdigris" -> verdigris
        "kawaii_light" -> kawaiiLight
        "kawaii_dark" -> kawaiiDark
        else -> material(ctx) ?: if (systemDark(ctx)) dark else light
    }

    fun systemDark(ctx: Context) =
        ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /**
     * Material You: the colours Android derives from the wallpaper, light or dark with the system,
     * the same mapping Corkboard uses. Read fresh each time the keyboard opens, so a new wallpaper
     * shows up the next time it appears.
     */
    private fun material(ctx: Context): Palette? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val r = ctx.resources
        fun col(id: Int) = r.getColor(id, null)
        return try {
            if (systemDark(ctx)) {
                Palette(
                    true,
                    bg = col(android.R.color.system_neutral1_900),
                    surface = col(android.R.color.system_neutral1_700),
                    text = col(android.R.color.system_neutral1_100),
                    textDim = col(android.R.color.system_neutral2_200),
                    accent = col(android.R.color.system_accent1_200),
                    onAccent = col(android.R.color.system_accent1_800),
                )
            } else {
                Palette(
                    false,
                    bg = col(android.R.color.system_neutral1_50),
                    surface = col(android.R.color.system_neutral1_10),
                    text = col(android.R.color.system_neutral1_900),
                    textDim = col(android.R.color.system_neutral2_700),
                    accent = col(android.R.color.system_accent1_600),
                    onAccent = col(android.R.color.system_accent1_0),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun dialogTheme(p: Palette) =
        if (p.isDark) android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
}
