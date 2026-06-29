package com.pearl.keyboard.settings

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.pearl.keyboard.BuildConfig
import com.pearl.keyboard.R

/**
 * Loads res/xml/preferences.xml. When the theme preference changes, re-applies night
 * mode and recreates the activity so the change is visible immediately.
 */
class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // About: fill in the live version and make the source row open the repo.
        findPreference<Preference>("pref_about_version")?.summary = BuildConfig.VERSION_NAME
        findPreference<Preference>("pref_about_source")?.setOnPreferenceClickListener {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LuminaGlpyhProd/PearlKeyboard"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Prefs.KEY_THEME) {
            SettingsActivity.applyNightModeFromPrefs(requireContext())
            activity?.recreate()
        }
    }
}
