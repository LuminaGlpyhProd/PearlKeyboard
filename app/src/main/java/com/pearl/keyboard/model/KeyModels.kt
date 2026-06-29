package com.pearl.keyboard.model

/**
 * Core, immutable description of the keyboard.
 *
 * A [KeyboardLayout] is a list of rows; each row is a list of [Key]s. Geometry
 * (pixel positions/sizes) is NOT stored here — it is computed at draw time by the
 * KeyboardView from the available width, so the same layout scales to any screen.
 */

/** What pressing a key does. The IME service interprets these. */
enum class KeyType {
    CHAR,        // emits a character (respecting current shift state)
    SHIFT,       // toggles shift / caps-lock
    DELETE,      // backspace (auto-repeats on hold)
    SPACE,
    ENTER,       // return / action key (Go, Search, Send, …)
    SYMBOLS,     // switch to another layout (see Key.targetLayout)
    LETTERS,     // switch back to the alphabetic layout
    EMOJI,       // open the emoji panel
    GLOBE,       // switch language / show the system IME picker
    NONE         // spacer / inert
}

/** Special keys are drawn as vector glyphs rather than text. */
enum class KeyIcon { NONE, SHIFT, DELETE, GLOBE, EMOJI, RETURN, MIC }

/** Identifies the selectable pages. */
enum class LayoutId { LETTERS, SYMBOLS, SYMBOLS2 }

/**
 * A single key.
 *
 * @param label       text shown on the key (for CHAR keys this is the lowercase form)
 * @param output      text emitted if different from [label]; otherwise [label] is used
 * @param type        behaviour, see [KeyType]
 * @param widthWeight relative width within its row (1f == one standard key)
 * @param popup       long-press alternates (e.g. e → è é ê …). Empty == none.
 * @param icon        glyph to draw instead of text (special keys)
 * @param targetLayout for SYMBOLS/LETTERS keys, which page to switch to
 */
data class Key(
    val label: String = "",
    val output: String? = null,
    val type: KeyType = KeyType.CHAR,
    val widthWeight: Float = 1f,
    val popup: List<String> = emptyList(),
    val icon: KeyIcon = KeyIcon.NONE,
    val targetLayout: LayoutId? = null
) {
    /** The text this key emits in its un-shifted form. */
    val text: String get() = output ?: label

    /** Special keys use the darker "function" background; letters/space use white. */
    val isFunctionKey: Boolean
        get() = type != KeyType.CHAR && type != KeyType.SPACE

    val isSpacer: Boolean get() = type == KeyType.NONE
}

/** One page of the keyboard. */
data class KeyboardLayout(
    val id: LayoutId,
    val rows: List<List<Key>>
)
