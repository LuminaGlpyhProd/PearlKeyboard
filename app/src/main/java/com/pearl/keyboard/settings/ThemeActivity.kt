package com.pearl.keyboard.settings

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.pearl.keyboard.feature.theme.BackgroundImage
import com.pearl.keyboard.ime.KeyAction
import com.pearl.keyboard.ime.KeyboardListener
import com.pearl.keyboard.ime.KeyboardView
import com.pearl.keyboard.model.KeyType
import com.pearl.keyboard.theme.KeyboardTheme
import com.pearl.keyboard.util.dpInt

/**
 * Theme customizer (#12): pick a background image, tune blur/brightness/dim, key
 * translucency and accent colour — with a live [KeyboardView] preview. All changes are
 * written straight to preferences, so the running keyboard reflects them next time it
 * opens.
 */
class ThemeActivity : AppCompatActivity() {

    private lateinit var sp: SharedPreferences
    private lateinit var preview: KeyboardView
    private var previewBmp: Bitmap? = null

    private val noop = object : KeyboardListener {
        override fun onAction(action: KeyAction) {}
        override fun onKeyDownFeedback(type: KeyType) {}
        override fun onGesturePreview(points: List<PointF>) {}
    }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val path = BackgroundImage.savePicked(this, uri)
                if (path != null) {
                    sp.edit().putString(Prefs.KEY_BG_PATH, path).apply()
                    reprocessAndPreview()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        SettingsActivity.applyNightModeFromPrefs(this)
        super.onCreate(savedInstanceState)
        sp = PreferenceManager.getDefaultSharedPreferences(this)
        title = "Theme & background"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dpInt(16f)
            setPadding(p, p, p, p)
        }

        root.addView(button("Choose background image") {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        })
        root.addView(button("Remove background") {
            sp.edit().putString(Prefs.KEY_BG_PATH, "").apply()
            BackgroundImage.clear(this)
            previewBmp = null
            applyPreview()
        })

        root.addView(seekRow("Background blur", Prefs.KEY_BG_BLUR, 0, 25, 0, reprocess = true))
        root.addView(seekRow("Background brightness", Prefs.KEY_BG_BRIGHTNESS, 30, 100, 100, reprocess = true))
        root.addView(seekRow("Background dim", Prefs.KEY_BG_DIM, 0, 80, 0, reprocess = false))
        root.addView(seekRow("Key opacity", Prefs.KEY_KEY_OPACITY, 30, 100, 100, reprocess = false))

        root.addView(label("Accent colour"))
        root.addView(accentRow())

        root.addView(label("Live preview"))
        preview = KeyboardView(this).apply { listener = noop }
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(ScrollView(this).apply { addView(root) })
        reprocessAndPreview()
    }

    // ---- preview ----------------------------------------------------------

    private fun reprocessAndPreview() {
        val path = sp.getString(Prefs.KEY_BG_PATH, "") ?: ""
        val blur = sp.getInt(Prefs.KEY_BG_BLUR, 0)
        val brightness = sp.getInt(Prefs.KEY_BG_BRIGHTNESS, 100)
        Thread {
            previewBmp = if (path.isEmpty()) null
            else runCatching { BackgroundImage.processed(this, path, blur, brightness) }.getOrNull()
            runOnUiThread { applyPreview() }
        }.start()
    }

    private fun applyPreview() {
        if (!::preview.isInitialized) return
        val theme = KeyboardTheme.build(
            this,
            sp.getString(Prefs.KEY_THEME_PRESET, "default") ?: "default",
            sp.getString(Prefs.KEY_THEME, "system") ?: "system",
            sp.getBoolean(Prefs.KEY_KEY_BORDERS, false),
            sp.getInt(Prefs.KEY_ACCENT, 0),
            sp.getInt(Prefs.KEY_KEY_OPACITY, 100)
        )
        preview.setTheme(theme)
        preview.setBackgroundImage(previewBmp, sp.getInt(Prefs.KEY_BG_DIM, 0))
    }

    // ---- small UI builders ------------------------------------------------

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setPadding(0, dpInt(14f), 0, dpInt(4f))
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setOnClickListener { onClick() }
    }

    private fun seekRow(text: String, key: String, min: Int, max: Int, default: Int, reprocess: Boolean): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tv = label("$text: ${sp.getInt(key, default)}")
        val sb = SeekBar(this).apply {
            this.min = min
            this.max = max
            progress = sp.getInt(key, default).coerceIn(min, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, value: Int, fromUser: Boolean) {
                    tv.text = "$text: $value"
                    sp.edit().putInt(key, value).apply()
                    if (reprocess) reprocessAndPreview() else applyPreview()
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        container.addView(tv)
        container.addView(sb)
        return container
    }

    private fun accentRow(): View {
        val scroller = HorizontalScrollView(this)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val swatches = listOf(
            "Default" to 0,
            "Blue" to 0xFF007AFF.toInt(),
            "Red" to 0xFFFF3B30.toInt(),
            "Green" to 0xFF34C759.toInt(),
            "Orange" to 0xFFFF9500.toInt(),
            "Purple" to 0xFFAF52DE.toInt(),
            "Pink" to 0xFFFF2D55.toInt(),
            "Teal" to 0xFF5AC8FA.toInt()
        )
        for ((name, color) in swatches) {
            val b = Button(this).apply {
                text = name
                if (color != 0) setBackgroundColor(color)
                setTextColor(if (color == 0 || isLight(color)) Color.BLACK else Color.WHITE)
                setOnClickListener {
                    sp.edit().putInt(Prefs.KEY_ACCENT, color).apply()
                    applyPreview()
                }
            }
            row.addView(b, LinearLayout.LayoutParams(dpInt(92f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dpInt(8f)
            })
        }
        scroller.addView(row)
        return scroller
    }

    private fun isLight(color: Int): Boolean {
        val l = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        return l > 160
    }
}
