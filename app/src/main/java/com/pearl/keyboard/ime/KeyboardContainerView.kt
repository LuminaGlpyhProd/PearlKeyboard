package com.pearl.keyboard.ime

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
    private val inlineScroller = HorizontalScrollView(context)
    private val inlineRow = LinearLayout(context)

    init {
        orientation = VERTICAL
        inlineScroller.isHorizontalScrollBarEnabled = false
        inlineRow.orientation = HORIZONTAL
        inlineScroller.addView(inlineRow)
        inlineScroller.visibility = View.GONE
        addView(inlineScroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
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
        strip.visibility = if (inlineScroller.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    /** Replace the keyboard with a feature panel, matching the keyboard's height. */
    fun showPanel(panel: View) {
        val targetHeight = if (keyboardView.height > 0) keyboardView.height else context.dpInt(250f)
        content.removeAllViews()
        content.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, targetHeight))
        // Hide the strip + inline row while a panel is up (Gboard-style).
        strip.visibility = View.GONE
        inlineScroller.visibility = View.GONE
    }

    /** Show inline autofill / OTP suggestion views (#10) in place of the strip. */
    fun showInline(views: List<View>) {
        inlineRow.removeAllViews()
        for (v in views) {
            (v.parent as? ViewGroup)?.removeView(v)
            inlineRow.addView(v)
        }
        val has = views.isNotEmpty()
        inlineScroller.visibility = if (has) View.VISIBLE else View.GONE
        if (has) strip.visibility = View.GONE
        else if (!isShowingPanel) strip.visibility = View.VISIBLE
    }

    fun clearInline() {
        inlineRow.removeAllViews()
        inlineScroller.visibility = View.GONE
        if (!isShowingPanel) strip.visibility = View.VISIBLE
    }
}
