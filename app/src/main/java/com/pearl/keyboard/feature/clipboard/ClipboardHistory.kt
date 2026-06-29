package com.pearl.keyboard.feature.clipboard

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Recent clipboard entries, newest first, persisted across sessions.
 *
 * The IME service registers a ClipboardManager listener and calls [add] whenever the
 * system clipboard changes; the clipboard panel reads [all].
 */
object ClipboardHistory {

    private const val KEY = "clipboard_history"
    private const val SEP = ""   // record separator unlikely to appear in copied text
    private const val MAX = 40

    private val items = ArrayList<String>()
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        load()
    }

    @Synchronized
    fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        items.remove(t)
        items.add(0, t)
        while (items.size > MAX) items.removeAt(items.size - 1)
        save()
    }

    @Synchronized
    fun remove(text: String) {
        if (items.remove(text)) save()
    }

    @Synchronized
    fun clear() {
        items.clear()
        save()
    }

    @Synchronized
    fun all(): List<String> = ArrayList(items)

    private fun load() {
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext ?: return)
        val stored = sp.getString(KEY, "") ?: ""
        items.clear()
        if (stored.isNotEmpty()) items.addAll(stored.split(SEP).filter { it.isNotEmpty() })
    }

    private fun save() {
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext ?: return)
        sp.edit().putString(KEY, items.joinToString(SEP)).apply()
    }
}
