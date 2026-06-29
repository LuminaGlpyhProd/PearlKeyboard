package com.pearl.keyboard.theme

import android.content.Context
import android.content.res.Configuration

/**
 * Colour palette for the keyboard. Kept in code (not resource qualifiers) so the
 * force-light/dark preference, the built-in presets (#13) and the custom-theme
 * overrides (accent + key translucency, #12) can all be combined at runtime.
 *
 * All colour values are 0xAARRGGBB ints. It's a data class so [build] can [copy] a
 * preset with accent/opacity overrides applied.
 */
data class KeyboardTheme(
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

        private const val WHITE = 0xFFFFFFFF.toInt()

        fun light(keyBorders: Boolean) = KeyboardTheme(
            isDark = false,
            keyBorders = keyBorders,
            background = 0xFFD1D4DB.toInt(),
            keyBg = WHITE,
            keyBgPressed = 0xFFE4E6EA.toInt(),
            functionKeyBg = 0xFFABB0BA.toInt(),
            functionKeyBgPressed = WHITE,
            keyText = 0xFF000000.toInt(),
            functionKeyText = 0xFF000000.toInt(),
            accent = 0xFF007AFF.toInt(),
            accentText = WHITE,
            popupBg = WHITE,
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
            keyText = WHITE,
            functionKeyText = WHITE,
            accent = 0xFF0A84FF.toInt(),
            accentText = WHITE,
            popupBg = 0xFF6A6A6C.toInt(),
            popupText = WHITE,
            keyShadow = 0x55000000,
            keyBorder = 0x1FFFFFFF,
            suggestionText = WHITE,
            suggestionDivider = 0x33FFFFFF,
            gestureTrail = 0x800A84FF.toInt()
        )

        /** Compact factory: derives shadow/border/divider/trail from the core colours. */
        private fun make(
            isDark: Boolean, keyBorders: Boolean,
            bg: Int, keyBg: Int, keyBgPressed: Int, fnBg: Int, fnBgPressed: Int,
            text: Int, accent: Int, popupBg: Int, popupText: Int
        ) = KeyboardTheme(
            isDark = isDark, keyBorders = keyBorders,
            background = bg, keyBg = keyBg, keyBgPressed = keyBgPressed,
            functionKeyBg = fnBg, functionKeyBgPressed = fnBgPressed,
            keyText = text, functionKeyText = text,
            accent = accent, accentText = WHITE,
            popupBg = popupBg, popupText = popupText,
            keyShadow = if (isDark) 0x55000000 else 0x33000000,
            keyBorder = if (isDark) 0x1FFFFFFF else 0x14000000,
            suggestionText = text,
            suggestionDivider = if (isDark) 0x33FFFFFF else 0x22000000,
            gestureTrail = withAlpha(accent, 0x80)
        )

        // ---- built-in presets (#13) ---------------------------------------
        fun pureWhite(b: Boolean) = make(false, true, 0xFFF2F2F5.toInt(), WHITE, 0xFFE6E6EA.toInt(), 0xFFEDEDF0.toInt(), WHITE, 0xFF000000.toInt(), 0xFF007AFF.toInt(), WHITE, 0xFF000000.toInt())
        fun amoled(b: Boolean) = make(true, b, 0xFF000000.toInt(), 0xFF161618.toInt(), 0xFF262628.toInt(), 0xFF0A0A0B.toInt(), 0xFF161618.toInt(), WHITE, 0xFF0A84FF.toInt(), 0xFF262628.toInt(), WHITE)
        fun materialLight(b: Boolean) = make(false, b, 0xFFE7E0EC.toInt(), WHITE, 0xFFEADDFF.toInt(), 0xFFCAC4D0.toInt(), WHITE, 0xFF1C1B1F.toInt(), 0xFF6750A4.toInt(), WHITE, 0xFF1C1B1F.toInt())
        fun materialDark(b: Boolean) = make(true, b, 0xFF1C1B1F.toInt(), 0xFF49454F.toInt(), 0xFF635B70.toInt(), 0xFF2B2930.toInt(), 0xFF49454F.toInt(), 0xFFE6E1E5.toInt(), 0xFFD0BCFF.toInt(), 0xFF49454F.toInt(), WHITE)
        fun blue(b: Boolean) = make(true, b, 0xFF0D1B2A.toInt(), 0xFF1B3A5B.toInt(), 0xFF26517F.toInt(), 0xFF122A45.toInt(), 0xFF1B3A5B.toInt(), WHITE, 0xFF4DA3FF.toInt(), 0xFF26517F.toInt(), WHITE)
        fun green(b: Boolean) = make(true, b, 0xFF0B1F14.toInt(), 0xFF16432B.toInt(), 0xFF1E5C3B.toInt(), 0xFF102E1E.toInt(), 0xFF16432B.toInt(), WHITE, 0xFF34C759.toInt(), 0xFF1E5C3B.toInt(), WHITE)
        // "Glass" presets pair with a background image: keys are translucent.
        fun glassLight(b: Boolean) = make(false, false, 0xFFB9BDC6.toInt(), withAlpha(WHITE, 0xC8), withAlpha(WHITE, 0xE6), withAlpha(0xFFAAB0BB.toInt(), 0xB4), withAlpha(WHITE, 0xD2), 0xFF000000.toInt(), 0xFF007AFF.toInt(), withAlpha(WHITE, 0xF0), 0xFF000000.toInt())
        fun glassDark(b: Boolean) = make(true, false, 0xFF2A2A2C.toInt(), withAlpha(0xFF3A3A3C.toInt(), 0xC8), withAlpha(0xFF515153.toInt(), 0xE6), withAlpha(0xFF2C2C2E.toInt(), 0xB4), withAlpha(0xFF3A3A3C.toInt(), 0xD2), WHITE, 0xFF0A84FF.toInt(), withAlpha(0xFF6A6A6C.toInt(), 0xF0), WHITE)

        /**
         * Build the final theme from preferences: pick a preset, then apply the
         * custom accent and key-opacity overrides.
         *
         * @param accentOverride 0 = keep preset accent
         * @param keyOpacityPercent 100 = opaque
         */
        fun build(
            context: Context, presetId: String, themeMode: String, keyBorders: Boolean,
            accentOverride: Int, keyOpacityPercent: Int
        ): KeyboardTheme {
            val useDark = isResolvedDark(context, themeMode)
            var t = when (presetId) {
                "pure_white" -> pureWhite(keyBorders)
                "amoled" -> amoled(keyBorders)
                "material_light" -> materialLight(keyBorders)
                "material_dark" -> materialDark(keyBorders)
                "blue" -> blue(keyBorders)
                "green" -> green(keyBorders)
                "glass" -> if (useDark) glassDark(keyBorders) else glassLight(keyBorders)
                else -> if (useDark) dark(keyBorders) else light(keyBorders)
            }
            if (accentOverride != 0) {
                t = t.copy(accent = accentOverride, gestureTrail = withAlpha(accentOverride, 0x80))
            }
            if (keyOpacityPercent in 0..99) {
                val a = (keyOpacityPercent * 255 / 100)
                t = t.copy(
                    keyBg = withAlpha(t.keyBg, a),
                    keyBgPressed = withAlpha(t.keyBgPressed, a),
                    functionKeyBg = withAlpha(t.functionKeyBg, a),
                    functionKeyBgPressed = withAlpha(t.functionKeyBgPressed, a)
                )
            }
            return t
        }

        /** Backwards-compatible simple resolver (default Pearl light/dark). */
        fun resolve(context: Context, mode: String, keyBorders: Boolean): KeyboardTheme =
            if (isResolvedDark(context, mode)) dark(keyBorders) else light(keyBorders)

        private fun isResolvedDark(context: Context, mode: String): Boolean = when (mode) {
            "light" -> false
            "dark" -> true
            else -> isSystemDark(context)
        }

        private fun isSystemDark(context: Context): Boolean {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return night == Configuration.UI_MODE_NIGHT_YES
        }

        /** Replace a colour's alpha channel. */
        fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
