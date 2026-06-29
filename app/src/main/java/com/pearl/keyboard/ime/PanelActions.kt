package com.pearl.keyboard.ime

import android.net.Uri

/** Callbacks a feature panel (emoji, clipboard, GIF) uses to talk to the IME. */
interface PanelActions {
    /** Insert text at the cursor. */
    fun commitText(text: String)
    /** Delete one character/emoji before the cursor. */
    fun backspace()
    /** Close the panel and return to the alphabetic keyboard. */
    fun closePanel()
    /** Insert rich media (e.g. a GIF) via the input-content API, with a text fallback. */
    fun commitMedia(uri: Uri, mimeType: String, fallbackText: String)
}
