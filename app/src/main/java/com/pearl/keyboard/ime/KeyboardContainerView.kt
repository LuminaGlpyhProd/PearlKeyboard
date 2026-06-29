package com.pearl.keyboard.ime

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.pearl.keyboard.theme.KeyboardTheme
import com.pearl.keyboard.util.dpInt

/**
 * Root input view: the suggestion strip stacked above a content area that holds
 * either the [KeyboardView] or a feature panel (emoji / clipboard / GIF).
 */
class KeyboardContainerView(context: Context) : LinearLayout(context) {

    val strip = SuggestionStripView(context)
    val keyboardView = KeyboardView(context)
    private val content = FrameLayout(context)

    init {
        orientation = VERTICAL
        addView(strip, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.addView(keyboardView, frameWrap())
    }

    private fun frameWrap() =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    fun setTheme(theme: KeyboardTheme) {
        setBackgroundColor(theme.background)
        strip.setTheme(theme)
        keyboardView.setTheme(theme)
    }

    val isShowingPanel: Boolean
        get() = content.getChildAt(0) !== keyboardView

    fun showKeyboard() {
        if (isShowingPanel) {
            content.removeAllViews()
            content.addView(keyboardView, frameWrap())
        }
        strip.visibility = View.VISIBLE
    }

    /** Replace the keyboard with a feature panel, matching the keyboard's height. */
    fun showPanel(panel: View) {
        val targetHeight = if (keyboardView.height > 0) keyboardView.height else context.dpInt(250f)
        content.removeAllViews()
        content.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, targetHeight))
        // Hide the strip while a panel is up to give it the full area (Gboard-style).
        strip.visibility = View.GONE
    }
}
