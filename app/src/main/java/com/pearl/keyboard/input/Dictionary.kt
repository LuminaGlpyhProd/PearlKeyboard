package com.pearl.keyboard.input

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * In-memory word list backing predictions, autocorrect and the glide decoder.
 *
 * Words are loaded from assets/dictionaries/en_words.txt in (approximate) frequency
 * order — earlier == more frequent. Words the user accepts are remembered in
 * SharedPreferences and treated as maximally frequent ("personalised suggestions").
 *
 * For a handful of hundred-to-thousand words a linear scan is plenty fast. For a
 * full production dictionary, swap the internals for a trie / FST (see README).
 */
class Dictionary(context: Context) {

    private val appContext = context.applicationContext

    /** Asset words, most-frequent first. */
    private val ordered = ArrayList<String>()
    private val rank = HashMap<String, Int>()
    private val userWords = LinkedHashSet<String>()

    init {
        loadAssets()
        loadUserWords()
    }

    private fun loadAssets() {
        runCatching {
            appContext.assets.open("dictionaries/en_words.txt").bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val w = raw.trim().lowercase()
                    if (w.isEmpty() || w.startsWith("#")) continue
                    if (rank.containsKey(w)) continue
                    rank[w] = ordered.size
                    ordered.add(w)
                }
            }
        }
    }

    private fun loadUserWords() {
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
        sp.getStringSet(KEY_USER_WORDS, emptySet())?.forEach { userWords.add(it) }
    }

    fun contains(word: String): Boolean {
        val w = word.lowercase()
        return rank.containsKey(w) || userWords.contains(w)
    }

    /** 0f (unknown / rare) … 1f (most frequent). User words score the maximum. */
    fun frequencyScore(word: String): Float {
        val w = word.lowercase()
        if (userWords.contains(w)) return 1f
        val idx = rank[w] ?: return 0f
        if (ordered.isEmpty()) return 0f
        return 1f - idx.toFloat() / ordered.size
    }

    /** Up to [limit] words beginning with [prefix], most-frequent first, user words prioritised. */
    fun wordsWithPrefix(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        val out = ArrayList<String>(limit)
        for (w in userWords) if (w.startsWith(p) && w != p) { out.add(w); if (out.size >= limit) return out }
        for (w in ordered) {
            if (w.startsWith(p) && w != p && w !in out) {
                out.add(w)
                if (out.size >= limit) break
            }
        }
        return out
    }

    /** Snapshot for the glide decoder to score against. */
    fun snapshot(): List<String> =
        if (userWords.isEmpty()) ordered else ArrayList<String>(userWords).apply { addAll(ordered) }

    /** Remember a word the user typed/accepted so it shows up next time. */
    fun learn(word: String) {
        val w = word.lowercase()
        if (w.length < 2 || rank.containsKey(w) || !userWords.add(w)) return
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
        sp.edit().putStringSet(KEY_USER_WORDS, HashSet(userWords)).apply()
    }

    companion object {
        private const val KEY_USER_WORDS = "pref_user_words"
    }
}
