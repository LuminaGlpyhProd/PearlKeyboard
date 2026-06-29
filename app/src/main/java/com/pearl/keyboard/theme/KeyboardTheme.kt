package com.pearl.keyboard.theme

import android.content.Context
import android.content.res.Configuration

/**
 * Colour palette for the keyboard, tuned to resemble the iOS keyboard in light
 * and dark appearance. Kept in code (not resource qualifiers) so the in-app
 * "force light / force dark" preference can override the system setting.
 *
 * All values are 0xAARRGGBB ints.
 */
class KeyboardTheme(
    val isDark: Boolean,
    val keyBorders: Boolean,
    val background: Int,
    val keyBg: Int,
    val keyBgPressed: Int,
    val functionKeyBg: Int,
    val functionKeyBgPressed: Int,
    val keyText: Int,
    val functionKeyText: Int,
    val accent: Int,
    val accentText: Int,
    val popupBg: Int,
    val popupText: Int,
    val keyShadow: Int,
    val keyBorder: Int,
    val suggestionText: Int,
    val suggestionDivider: Int,
    val gestureTrail: Int
) {
    companion object {

        fun light(keyBorders: Boolean) = KeyboardTheme(
            isDark = false,
            keyBorders = keyBorders,
            background = 0xFFD1D4DB.toInt(),
            keyBg = 0xFFFFFFFF.toInt(),
            keyBgPressed = 0xFFE4E6EA.toInt(),
            functionKeyBg = 0xFFABB0BA.toInt(),
            functionKeyBgPressed = 0xFFFFFFFF.toInt(),
            keyText = 0xFF000000.toInt(),
            functionKeyText = 0xFF000000.toInt(),
            accent = 0xFF007AFF.toInt(),
            accentText = 0xFFFFFFFF.toInt(),
            popupBg = 0xFFFFFFFF.toInt(),
            popupText = 0xFF000000.toInt(),
            keyShadow = 0x33000000,
            keyBorder = 0x14000000,
            suggestionText = 0xFF000000.toInt(),
            suggestionDivider = 0x22000000,
            gestureTrail = 0x80007AFF.toInt()
        )

        fun dark(keyBorders: Boolean) = KeyboardTheme(
            isDark = true,
            keyBorders = keyBorders,
            background = 0xFF1C1C1E.toInt(),
            keyBg = 0xFF3A3A3C.toInt(),
            keyBgPressed = 0xFF515153.toInt(),
            functionKeyBg = 0xFF2C2C2E.toInt(),
            functionKeyBgPressed = 0xFF3A3A3C.toInt(),
            keyText = 0xFFFFFFFF.toInt(),
            functionKeyText = 0xFFFFFFFF.toInt(),
            accent = 0xFF0A84FF.toInt(),
            accentText = 0xFFFFFFFF.toInt(),
            popupBg = 0xFF6A6A6C.toInt(),
            popupText = 0xFFFFFFFF.toInt(),
            keyShadow = 0x55000000,
            keyBorder = 0x1FFFFFFF,
            suggestionText = 0xFFFFFFFF.toInt(),
            suggestionDivider = 0x33FFFFFF,
            gestureTrail = 0x800A84FF.toInt()
        )

        /** @param mode one of "system", "light", "dark" (from preferences). */
        fun resolve(context: Context, mode: String, keyBorders: Boolean): KeyboardTheme {
            val dark = when (mode) {
                "light" -> false
                "dark" -> true
                else -> isSystemDark(context)
            }
            return if (dark) dark(keyBorders) else light(keyBorders)
        }

        private fun isSystemDark(context: Context): Boolean {
            val night = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
            return night == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
