package com.pearl.keyboard.feature.emoji

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Static emoji catalogue grouped into categories, plus a persisted "recents" list.
 * This is a representative subset; extend the lists freely — the panel is data-driven.
 */
object EmojiData {

    data class Category(val tab: String, val emojis: List<String>)

    private const val KEY_RECENTS = "emoji_recents"
    private const val SEP = ""   // record separator unlikely to appear in an emoji
    private const val MAX_RECENTS = 30

    private val recents = ArrayList<String>()
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext!!)
        val stored = sp.getString(KEY_RECENTS, "") ?: ""
        if (stored.isNotEmpty()) recents.addAll(stored.split(SEP).filter { it.isNotEmpty() })
    }

    fun recents(): List<String> = ArrayList(recents)

    fun addRecent(emoji: String) {
        recents.remove(emoji)
        recents.add(0, emoji)
        while (recents.size > MAX_RECENTS) recents.removeAt(recents.size - 1)
        appContext?.let {
            PreferenceManager.getDefaultSharedPreferences(it)
                .edit().putString(KEY_RECENTS, recents.joinToString(SEP)).apply()
        }
    }

    val smileys = "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🫡 🤭 🫢 😶 😐 😑 😬 🙄 😯 😴 🤤 😪 😮‍💨 🥱".trim().split(" ")
    val animals = "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🪲 🐢 🐍 🦖 🐙 🦑 🦀 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦛 🐪 🐫 🦒".trim().split(" ")
    val food = "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🥯 🍞 🥖 🧀 🥚 🍳 🧇 🥞 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🥗 🍣 🍱 🍜 🍝 🍦 🍩 🍪 🎂 🍰 🍫 🍬 🍭 ☕ 🍵 🍺 🍷".trim().split(" ")
    val activities = "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🎱 🏓 🏸 🥅 🏒 🏑 🥍 🏏 ⛳ 🪁 🏹 🎣 🤿 🥊 🥋 🎽 🛹 🛼 🛷 ⛸️ 🥌 🎿 ⛷️ 🏂 🏋️ 🤸 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🚴 🚵 🎯 🎮 🎲 🎰 🎳".trim().split(" ")
    val travel = "🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🏍️ 🛵 🚲 🛴 ✈️ 🚀 🛸 🚁 ⛵ 🚤 🛳️ ⚓ 🚂 🚆 🚄 🗽 🗼 🏰 🏯 🎡 🎢 🎠 ⛲ 🏖️ 🏝️ ⛰️ 🌋 🗻 🏕️ 🌅 🌄 🌠 🎇 🌉".trim().split(" ")
    val objects = "⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💽 💾 📷 📸 🎥 📺 📻 🎙️ ⏰ 🔋 🔌 💡 🔦 🕯️ 🧯 🛢️ 💸 💵 💳 🔧 🔨 🛠️ ⚙️ 🧲 🔫 💣 🔪 🚪 🛏️ 🚽 🚿 🛁 🧴 🧷 🧹 🧺 🔑 🔒 📦 📚 ✏️ 📌 📎".trim().split(" ")
    val symbols = "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 ✅ ❌ ⭕ ❗ ❓ 💯 🔥 ⭐ 🌟 ✨ ⚡ 💥 💫 🎉 🎊 ➕ ➖ ➗ ✖️ ♻️ ✔️ ☑️ 🔘 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪".trim().split(" ")

    /** Categories in display order. Index 0 ("🕘") is a placeholder filled with [recents]. */
    val categories: List<Category> = listOf(
        Category("🕘", emptyList()),
        Category("😀", smileys),
        Category("🐶", animals),
        Category("🍔", food),
        Category("⚽", activities),
        Category("🚗", travel),
        Category("💡", objects),
        Category("❤️", symbols)
    )
}
