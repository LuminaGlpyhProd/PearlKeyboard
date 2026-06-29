package com.pearl.keyboard.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Typed, read-only-ish facade over the default SharedPreferences.
 *
 * Keys MUST match res/xml/preferences.xml. Values are read live on each access so
 * changes from the settings screen take effect the next time the keyboard reads them
 * (the IME also re-reads everything in onStartInputView).
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    // Appearance
    val themeMode: String get() = sp.getString(KEY_THEME, "system") ?: "system"
    val keyBorders: Boolean get() = sp.getBoolean(KEY_KEY_BORDERS, false)

    // Theme engine (#12 / #13)
    val themePreset: String get() = sp.getString(KEY_THEME_PRESET, "default") ?: "default"
    val accentColor: Int get() = sp.getInt(KEY_ACCENT, 0)            // 0 = preset default
    val keyOpacity: Int get() = sp.getInt(KEY_KEY_OPACITY, 100).coerceIn(30, 100)
    val bgImagePath: String get() = sp.getString(KEY_BG_PATH, "") ?: ""
    val bgBlur: Int get() = sp.getInt(KEY_BG_BLUR, 0).coerceIn(0, 25)
    val bgBrightness: Int get() = sp.getInt(KEY_BG_BRIGHTNESS, 100).coerceIn(30, 100)
    val bgDim: Int get() = sp.getInt(KEY_BG_DIM, 0).coerceIn(0, 80)

    /** 0.70f … 1.30f multiplier applied to the row height. */
    val keyboardHeightScale: Float get() = sp.getInt(KEY_HEIGHT, 100).coerceIn(70, 130) / 100f
    val popupPreview: Boolean get() = sp.getBoolean(KEY_POPUP, true)
    val oneHanded: Boolean get() = sp.getBoolean(KEY_ONE_HANDED, false)

    // Typing
    val autocorrect: Boolean get() = sp.getBoolean(KEY_AUTOCORRECT, true)
    val predictions: Boolean get() = sp.getBoolean(KEY_PREDICTIONS, true)
    val gestureTyping: Boolean get() = sp.getBoolean(KEY_GESTURE, true)
    val doubleSpacePeriod: Boolean get() = sp.getBoolean(KEY_DOUBLE_SPACE, true)
    val autoCap: Boolean get() = sp.getBoolean(KEY_AUTOCAP, true)
    /** Long-press delay before the accent bar opens, in ms. */
    val longPressDelay: Int get() = sp.getInt(KEY_LONG_PRESS, 320).coerceIn(150, 600)

    // Feedback
    val sound: Boolean get() = sp.getBoolean(KEY_SOUND, true)
    val soundVolume: Float get() = sp.getInt(KEY_SOUND_VOLUME, 40).coerceIn(0, 100) / 100f
    val haptics: Boolean get() = sp.getBoolean(KEY_HAPTIC, true)
    /** Sound pack id: iphone | android | gboard | mechanical | soft | retro | silent. */
    val soundPack: String get() = sp.getString(KEY_SOUND_PACK, "iphone") ?: "iphone"

    // Features
    val clipboardEnabled: Boolean get() = sp.getBoolean(KEY_CLIPBOARD, true)
    val voiceEnabled: Boolean get() = sp.getBoolean(KEY_VOICE, true)
    val gifEnabled: Boolean get() = sp.getBoolean(KEY_GIF, false)
    val autoUpdate: Boolean get() = sp.getBoolean(KEY_AUTO_UPDATE, true)
    val autofillEnabled: Boolean get() = sp.getBoolean(KEY_AUTOFILL, true)

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val KEY_THEME = "pref_theme"
        const val KEY_KEY_BORDERS = "pref_key_borders"
        const val KEY_THEME_PRESET = "pref_theme_preset"
        const val KEY_ACCENT = "pref_accent_color"
        const val KEY_KEY_OPACITY = "pref_key_opacity"
        const val KEY_BG_PATH = "pref_bg_image_path"
        const val KEY_BG_BLUR = "pref_bg_blur"
        const val KEY_BG_BRIGHTNESS = "pref_bg_brightness"
        const val KEY_BG_DIM = "pref_bg_dim"
        const val KEY_HEIGHT = "pref_keyboard_height"
        const val KEY_POPUP = "pref_popup_preview"
        const val KEY_ONE_HANDED = "pref_one_handed"

        const val KEY_AUTOCORRECT = "pref_autocorrect"
        const val KEY_PREDICTIONS = "pref_predictions"
        const val KEY_GESTURE = "pref_gesture"
        const val KEY_DOUBLE_SPACE = "pref_double_space_period"
        const val KEY_AUTOCAP = "pref_autocap"
        const val KEY_LONG_PRESS = "pref_long_press_delay"

        const val KEY_SOUND = "pref_sound"
        const val KEY_SOUND_PACK = "pref_sound_pack"
        const val KEY_SOUND_VOLUME = "pref_sound_volume"
        const val KEY_HAPTIC = "pref_haptic"

        const val KEY_CLIPBOARD = "pref_clipboard"
        const val KEY_VOICE = "pref_voice"
        const val KEY_GIF = "pref_gif"
        const val KEY_AUTO_UPDATE = "pref_auto_update"
        const val KEY_AUTOFILL = "pref_autofill"
    }
}
