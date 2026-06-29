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
    private const val ACCEPT_THRESHOLD = 1.45 // normalised average distance; lower = stricter

    fun decode(points: List<PointF>, centers: Map<Char, PointF>, dict: Dictionary): String? {
        if (points.size < 3 || centers.isEmpty()) return null

        val path = resample(points, SAMPLES)
        val firstKey = nearestKey(path.first(), centers) ?: return null
        val lastKey = nearestKey(path.last(), centers) ?: return null
        val pitch = averageKeyPitch(centers).coerceAtLeast(1.0)

        var best: String? = null
        var bestScore = Double.MAX_VALUE
        for (w in dict.snapshot()) {
            if (w.length < 2) continue
            if (w.first() != firstKey || w.last() != lastKey) continue
            val cost = alignCost(w, path, centers, pitch) ?: continue
            val score = cost - dict.frequencyScore(w) * 0.5
            if (score < bestScore) {
                bestScore = score
                best = w
            }
        }
        return best?.takeIf { bestScore < ACCEPT_THRESHOLD }
    }

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

    private fun nearestKey(p: PointF, centers: Map<Char, PointF>): Char? {
        var bestChar: Char? = null
        var bestD = Double.MAX_VALUE
        for ((ch, c) in centers) {
            val d = dist(p, c)
            if (d < bestD) {
                bestD = d
                bestChar = ch
            }
        }
        return bestChar
    }

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
