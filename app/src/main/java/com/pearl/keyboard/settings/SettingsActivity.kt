package com.pearl.keyboard.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.pearl.keyboard.R
import com.pearl.keyboard.feature.update.UpdateChecker

/**
 * Host screen: onboarding (enable + pick the keyboard, plus a field to try it) and
 * the preferences fragment. Also applies the light/dark preference to itself.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyNightModeFromPrefs(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<MaterialButton>(R.id.btn_enable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<MaterialButton>(R.id.btn_choose).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        // Automatic update check (silent if already current). User confirms before install.
        if (Prefs(this).autoUpdate) UpdateChecker.checkAndPrompt(this, silent = true)
    }

    companion object {
        /** Map the theme preference to AppCompat's night mode for the host UI. */
        fun applyNightModeFromPrefs(context: Context) {
            val mode = when (Prefs(context).themeMode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
