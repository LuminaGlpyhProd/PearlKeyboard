package com.pearl.keyboard.ime

/** Callbacks a feature panel (emoji, clipboard, GIF) uses to talk to the IME. */
interface PanelActions {
    /** Insert text at the cursor. */
    fun commitText(text: String)
    /** Delete one character/emoji before the cursor. */
    fun backspace()
    /** Close the panel and return to the alphabetic keyboard. */
    fun closePanel()
}
