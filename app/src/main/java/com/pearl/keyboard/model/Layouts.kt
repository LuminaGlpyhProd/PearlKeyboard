package com.pearl.keyboard.model

import com.pearl.keyboard.model.KeyType.CHAR
import com.pearl.keyboard.model.KeyType.DELETE
import com.pearl.keyboard.model.KeyType.EMOJI
import com.pearl.keyboard.model.KeyType.ENTER
import com.pearl.keyboard.model.KeyType.GLOBE
import com.pearl.keyboard.model.KeyType.LETTERS
import com.pearl.keyboard.model.KeyType.SHIFT
import com.pearl.keyboard.model.KeyType.SPACE
import com.pearl.keyboard.model.KeyType.SYMBOLS

/**
 * The built-in iOS-style layouts: QWERTY letters, and two symbol pages.
 *
 * Rows are intentionally allowed to be "narrower" than the widest row — the
 * KeyboardView centres each row, which reproduces the inset look of the iOS
 * A-S-D-F row and the shift/return rows.
 *
 * To add a language, create a new letters layout here and a matching <subtype>
 * in res/xml/method.xml.
 */
object Layouts {

    // ---- small builders ---------------------------------------------------

    /** A character key with optional long-press alternates. */
    private fun c(label: String, popup: String = ""): Key =
        Key(label = label, popup = popup.map { it.toString() })

    private val shift = Key(type = SHIFT, icon = KeyIcon.SHIFT, widthWeight = 1.3f)
    private val del = Key(type = DELETE, icon = KeyIcon.DELETE, widthWeight = 1.3f)
    private val globe = Key(type = GLOBE, icon = KeyIcon.GLOBE, widthWeight = 1.1f)
    private val emoji = Key(type = EMOJI, icon = KeyIcon.EMOJI, widthWeight = 1.1f)
    private val enter = Key(label = "return", type = ENTER, widthWeight = 2.2f)
    private val space = Key(label = "space", type = SPACE, output = " ", widthWeight = 4.2f)

    private fun to123() = Key(label = "123", type = SYMBOLS, targetLayout = LayoutId.SYMBOLS, widthWeight = 1.4f)
    private fun toABC() = Key(label = "ABC", type = LETTERS, targetLayout = LayoutId.LETTERS, widthWeight = 1.4f)
    private fun toSym2() = Key(label = "#+=", type = SYMBOLS, targetLayout = LayoutId.SYMBOLS2, widthWeight = 1.3f)

    /** Shared bottom row; [leftSwitch] is 123 (letters) or ABC (symbols). */
    private fun bottomRow(leftSwitch: Key): List<Key> =
        listOf(leftSwitch, globe, emoji, space, enter)

    // ---- letters ----------------------------------------------------------

    val letters = KeyboardLayout(
        id = LayoutId.LETTERS,
        rows = listOf(
            listOf(
                c("q"), c("w"), c("e", "èéêëēėę"), c("r"), c("t"),
                c("y", "ÿ"), c("u", "ûüùúū"), c("i", "îïíīįì"),
                c("o", "ôöòóœøōõ"), c("p")
            ),
            listOf(
                c("a", "àáâäæãåā"), c("s", "ßśš"), c("d"), c("f"), c("g"),
                c("h"), c("j"), c("k"), c("l", "ł")
            ),
            listOf(
                shift,
                c("z", "žźż"), c("x"), c("c", "çćč"), c("v"),
                c("b"), c("n", "ñń"), c("m"),
                del
            ),
            bottomRow(to123())
        )
    )

    // ---- symbols page 1 ----------------------------------------------------

    val symbols = KeyboardLayout(
        id = LayoutId.SYMBOLS,
        rows = listOf(
            listOf(
                c("1"), c("2"), c("3"), c("4"), c("5"),
                c("6"), c("7"), c("8"), c("9"), c("0")
            ),
            listOf(
                c("-", "–—•"), c("/"), c(":"), c(";"), c("("),
                c(")"), c("$", "€£¥₩"), c("&"), c("@"), c("\"", "“”„")
            ),
            listOf(
                toSym2(),
                c("."), c(","), c("?", "¿"), c("!", "¡"), c("'", "’‘`"),
                del
            ),
            bottomRow(toABC())
        )
    )

    // ---- symbols page 2 (#+=) ---------------------------------------------

    val symbols2 = KeyboardLayout(
        id = LayoutId.SYMBOLS2,
        rows = listOf(
            listOf(
                c("["), c("]"), c("{"), c("}"), c("#"),
                c("%"), c("^"), c("*"), c("+"), c("=")
            ),
            listOf(
                c("_"), c("\\"), c("|"), c("~"), c("<"),
                c(">"), c("€"), c("£"), c("¥"), c("•")
            ),
            listOf(
                to123(),
                c("."), c(","), c("?"), c("!"), c("'"),
                del
            ),
            bottomRow(toABC())
        )
    )

    fun byId(id: LayoutId): KeyboardLayout = when (id) {
        LayoutId.LETTERS -> letters
        LayoutId.SYMBOLS -> symbols
        LayoutId.SYMBOLS2 -> symbols2
    }
}
