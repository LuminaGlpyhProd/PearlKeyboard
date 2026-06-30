package com.pearl.keyboard.input

import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.min

/**
 * A deliberately simple glide/swipe decoder.
 *
 * Approach (a lightweight SHARK²-style scorer):
 *   1. Resample the raw finger path to a fixed number of evenly spaced points.
 *   2. Restrict candidates to dictionary words whose FIRST and LAST letters match
 *      the keys under the start/end of the swipe (a strong, cheap filter).
 *   3. Score each candidate by dynamic-programming alignment of the path points to
 *      the candidate's key centres (monotonic), minimising total point-to-key
 *      distance, normalised by key pitch. Frequency provides a small tie-break.
 *
 * This is NOT Gboard's production neural decoder. It handles clean swipes of common
 * words well and is easy to reason about; see README for how to upgrade it.
 */
object GestureTypingDetector {

    private const val SAMPLES = 24
    // Accept the N nearest keys to the swipe's start/end (not just the single closest),
    // so a slightly-off start or end still finds the intended word.
    private const val ENDPOINT_KEYS = 2

    /**
     * Rank dictionary words for this swipe, best first (up to [max]).
     *
     * Unlike a strict matcher this ALWAYS returns its best guesses whenever any word fits
     * the start/end keys — so the caller can auto-insert the top word the moment the
     * finger lifts (Gboard-style) and still offer the rest as alternatives, instead of
     * the user having to tap the strip.
     */
    fun decodeCandidates(
        points: List<PointF>, centers: Map<Char, PointF>, dict: Dictionary, max: Int
    ): List<String> {
        if (points.size < 3 || centers.isEmpty()) return emptyList()

        val path = resample(points, SAMPLES)
        val startKeys = nearestKeys(path.first(), centers, ENDPOINT_KEYS)
        val endKeys = nearestKeys(path.last(), centers, ENDPOINT_KEYS)
        if (startKeys.isEmpty() || endKeys.isEmpty()) return emptyList()
        val pitch = averageKeyPitch(centers).coerceAtLeast(1.0)

        val scored = ArrayList<Pair<String, Double>>()
        for (w in dict.snapshot()) {
            if (w.length < 2) continue
            if (w[0] !in startKeys || w[w.length - 1] !in endKeys) continue
            val cost = alignCost(w, path, centers, pitch) ?: continue
            // Lower cost is better; a small frequency bonus breaks ties toward common words.
            scored.add(w to (cost - dict.frequencyScore(w) * 0.5))
        }
        scored.sortBy { it.second }
        return scored.take(max).map { it.first }
    }

    /** The single best word for this swipe (or null if nothing fits the endpoints). */
    fun decode(points: List<PointF>, centers: Map<Char, PointF>, dict: Dictionary): String? =
        decodeCandidates(points, centers, dict, 1).firstOrNull()

    private fun alignCost(word: String, path: List<PointF>, centers: Map<Char, PointF>, pitch: Double): Double? {
        val l = word.length
        val c = Array(l) { centers[word[it]] ?: return null }
        val n = path.size

        // dp[j] = min total cost to align the path so far, ending at letter j (monotonic).
        var prev = DoubleArray(l) { Double.MAX_VALUE }
        prev[0] = dist(path[0], c[0])
        for (i in 1 until n) {
            val curr = DoubleArray(l) { Double.MAX_VALUE }
            for (j in 0 until l) {
                val carried = if (j == 0) prev[0] else min(prev[j], prev[j - 1])
                if (carried != Double.MAX_VALUE) curr[j] = dist(path[i], c[j]) + carried
            }
            prev = curr
        }
        val total = prev[l - 1]
        if (total == Double.MAX_VALUE) return null
        return total / n / pitch
    }

    /** The [n] keys whose centres are closest to point [p]. */
    private fun nearestKeys(p: PointF, centers: Map<Char, PointF>, n: Int): Set<Char> =
        centers.entries
            .sortedBy { dist(p, it.value) }
            .take(n)
            .map { it.key }
            .toSet()

    private fun averageKeyPitch(centers: Map<Char, PointF>): Double {
        val list = centers.values.toList()
        if (list.size < 2) return 1.0
        var sum = 0.0
        for (a in list) {
            var nearest = Double.MAX_VALUE
            for (b in list) if (a !== b) nearest = min(nearest, dist(a, b))
            sum += nearest
        }
        return sum / list.size
    }

    private fun dist(a: PointF, b: PointF): Double =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    /** Resample a polyline to exactly [n] evenly spaced points. */
    private fun resample(points: List<PointF>, n: Int): List<PointF> {
        if (points.size <= 1) return List(n) { PointF(points.first().x, points.first().y) }
        var total = 0.0
        for (i in 1 until points.size) total += dist(points[i - 1], points[i])
        if (total <= 0.0) return List(n) { PointF(points.first().x, points.first().y) }

        val step = total / (n - 1)
        val out = ArrayList<PointF>(n)
        out.add(PointF(points[0].x, points[0].y))
        var prevX = points[0].x
        var prevY = points[0].y
        var i = 1
        var remaining = step
        while (out.size < n && i < points.size) {
            val cx = points[i].x
            val cy = points[i].y
            val segLen = hypot((cx - prevX).toDouble(), (cy - prevY).toDouble())
            if (segLen < remaining || segLen == 0.0) {
                remaining -= segLen
                prevX = cx
                prevY = cy
                i++
            } else {
                val t = (remaining / segLen).toFloat()
                prevX += t * (cx - prevX)
                prevY += t * (cy - prevY)
                out.add(PointF(prevX, prevY))
                remaining = step
            }
        }
        while (out.size < n) out.add(PointF(points.last().x, points.last().y))
        return out
    }
}
