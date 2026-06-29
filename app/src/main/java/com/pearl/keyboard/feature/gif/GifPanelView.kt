package com.pearl.keyboard.feature.gif

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.pearl.keyboard.ime.PanelActions
import com.pearl.keyboard.theme.KeyboardTheme
import com.pearl.keyboard.util.dpInt

/**
 * GIF search — SCAFFOLD.
 *
 * Real GIF search needs a provider (e.g. Tenor / Giphy), an API key, network code and
 * an image loader. None of those are bundled (no key to ship, and we avoid heavy
 * dependencies). This panel provides the entry point + wiring; see README
 * "Implementing GIF search" for how to finish it.
 *
 * To complete: put your key in [TENOR_API_KEY], call the Tenor `search` endpoint,
 * load thumbnails into a RecyclerView grid, and on tap insert the GIF URL/URI via
 * [actions]. Many chat apps accept a content:// image through the input-content API
 * (InputConnection#commitContent) — wire that for inline GIFs.
 */
@SuppressLint("ViewConstructor")
class GifPanelView(context: Context) : LinearLayout(context) {

    var actions: PanelActions? = null
    private val title = TextView(context)
    private val message = TextView(context)
    private val close = TextView(context)

    init {
        orientation = VERTICAL
        val pad = context.dpInt(16f)
        setPadding(pad, pad, pad, pad)

        val bar = LinearLayout(context).apply { orientation = HORIZONTAL }
        close.text = "ABC"
        close.textSize = 15f
        close.setPadding(0, 0, context.dpInt(16f), 0)
        close.setOnClickListener { actions?.closePanel() }
        title.text = "GIF"
        title.textSize = 17f
        bar.addView(close)
        bar.addView(title)
        addView(bar)

        message.text =
            "GIF search isn't configured yet.\n\nAdd a Tenor API key in GifPanelView.kt and " +
                "implement the search grid (see README). Tap ABC to go back."
        message.gravity = Gravity.CENTER
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        message.layoutParams = lp
        message.setPadding(0, context.dpInt(24f), 0, 0)
        addView(message)
    }

    fun setTheme(theme: KeyboardTheme) {
        setBackgroundColor(theme.background)
        title.setTextColor(theme.keyText)
        message.setTextColor(theme.suggestionText)
        close.setTextColor(theme.accent)
    }

    companion object {
        /** Put your Tenor v2 API key here to enable GIF search. */
        const val TENOR_API_KEY = ""
    }
}
