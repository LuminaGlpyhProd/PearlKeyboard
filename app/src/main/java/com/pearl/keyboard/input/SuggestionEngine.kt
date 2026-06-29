package com.pearl.keyboard.input

import kotlin.math.abs

/**
 * What to show in the suggestion strip for the word currently being typed.
 *
 * @param items         up to N strings to display (index 0 is always the literal text)
 * @param autoCorrection if non-null and the user presses space, replace the word with this
 */
data class Suggestions(val items: List<String>, val autoCorrection: String?)

/**
 * Turns the in-progress word into predictions + an autocorrect candidate.
 *
 * Predictions = dictionary words sharing the typed prefix (frequency ranked).
 * Autocorrect  = the closest dictionary word within a small edit distance when the
 * typed text isn't itself a word. Deliberately conservative so it never "fights" the
 * user — the literal text is always offered as the first chip so a correction can be
 * rejected with one tap, just like iOS.
 */
class SuggestionEngine(private val dict: Dictionary) {

    fun suggest(typed: String, max: Int = 3): Suggestions {
        if (typed.isBlank()) return Suggestions(emptyList(), null)
        val lower = typed.lowercase()
        val predictions = dict.wordsWithPrefix(lower, 8)

        val display = ArrayList<String>()
        val seen = HashSet<String>()
        fun add(s: String) {
            if (seen.add(s.lowercase())) display.add(s)
        }

        if (dict.contains(lower)) {
            add(typed)
            predictions.forEach(::add)
            return Suggestions(display.take(max), null)
        }

        val correction = bestCorrection(lower)
        add(typed)
        correction?.let(::add)
        predictions.forEach(::add)
        return Suggestions(display.take(max), correction)
    }

    /** Closest known word, or null if nothing is close enough. */
    private fun bestCorrection(word: String): String? {
        if (word.length < 2) return null
        val maxDist = if (word.length <= 4) 1 else 2
        var best: String? = null
        var bestKey = Double.MAX_VALUE
        for (cand in dict.snapshot()) {
            if (abs(cand.length - word.length) > maxDist) continue
            val d = editDistance(word, cand, maxDist)
            if (d in 0..maxDist) {
                // Prefer fewer edits, then higher frequency.
                val key = d.toDouble() - dict.frequencyScore(cand) * 0.9
                if (key < bestKey) {
                    bestKey = key
                    best = cand
                }
            }
        }
        return best?.takeIf { it != word }
    }

    /** Levenshtein distance with an early-out once it exceeds [max]. */
    private fun editDistance(s: String, t: String, max: Int): Int {
        val n = s.length
        val m = t.length
        if (abs(n - m) > max) return max + 1
        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)
        for (i in 1..n) {
            curr[0] = i
            var rowMin = curr[0]
            val sc = s[i - 1]
            for (j in 1..m) {
                val cost = if (sc == t[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > max) return max + 1
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[m]
    }
}
