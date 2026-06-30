package com.pearl.keyboard.feedback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generates short, ORIGINAL keypress clicks as 16-bit mono PCM WAV bytes.
 *
 * Nothing here is sampled from any product — each click is a synthesised tone +
 * filtered noise burst with a fast decay envelope, so the result is legally
 * distributable. The default "iphone" voice aims for the soft, brief, slightly
 * pitched tap character of a modern phone keyboard (recreated, not copied).
 *
 * Output is a complete .wav (header + data) ready to hand to SoundPool.
 */
object SoundSynth {

    private const val SAMPLE_RATE = 44100

    private data class Voice(
        val freq: Double,
        val noise: Double,    // 0 = pure tone, 1 = pure noise
        val decay: Double,    // larger = faster decay (shorter, snappier)
        val amp: Double,
        val square: Boolean,  // square wave => retro / harsher
        val durMs: Int
    )

    private fun voice(pack: String): Voice = when (pack) {
        "mechanical" -> Voice(2200.0, 0.55, 120.0, 0.55, false, 18)
        "soft" -> Voice(1400.0, 0.15, 55.0, 0.40, false, 30)
        "retro" -> Voice(900.0, 0.10, 70.0, 0.50, true, 26)
        "android", "gboard" -> Voice(1700.0, 0.35, 85.0, 0.50, false, 22)
        else -> /* iphone */ Voice(1900.0, 0.25, 90.0, 0.50, false, 22)
    }

    /** Per-key-type tweak: (frequency multiplier, duration multiplier). */
    private fun modifier(kind: String): Pair<Double, Double> = when (kind) {
        "delete" -> 0.82 to 1.10
        "return" -> 0.66 to 1.15
        "space" -> 0.45 to 1.35
        else -> 1.0 to 1.0  // "standard"
    }

    /** @param kind one of standard | delete | return | space */
    fun wav(pack: String, kind: String): ByteArray {
        val v = voice(pack)
        val (freqMul, durMul) = modifier(kind)
        val freq = v.freq * freqMul
        val durMs = (v.durMs * durMul).toInt().coerceAtLeast(8)
        val n = SAMPLE_RATE * durMs / 1000
        val pcm = ByteArray(n * 2)
        val rnd = Random(2026)                       // deterministic noise
        val attack = (SAMPLE_RATE * 0.0012).toInt().coerceAtLeast(1) // ~1.2ms fade-in (no pop)

        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            var env = exp(-t * v.decay)
            if (i < attack) env *= i.toDouble() / attack
            val phase = 2.0 * PI * freq * t
            val tone = if (v.square) (if (sin(phase) >= 0) 1.0 else -1.0) else sin(phase)
            val noise = rnd.nextDouble() * 2.0 - 1.0
            var s = env * ((1.0 - v.noise) * tone + v.noise * noise) * v.amp
            if (s > 1.0) s = 1.0 else if (s < -1.0) s = -1.0
            val q = (s * 32767.0).toInt()
            pcm[i * 2] = (q and 0xFF).toByte()
            pcm[i * 2 + 1] = ((q shr 8) and 0xFF).toByte()
        }
        return header(pcm.size) + pcm
    }

    private fun header(dataLen: Int): ByteArray {
        val channels = 1
        val bits = 16
        val byteRate = SAMPLE_RATE * channels * bits / 8
        val blockAlign = channels * bits / 8
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())             // PCM
            putShort(channels.toShort())
            putInt(SAMPLE_RATE)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bits.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataLen)
        }.array()
    }
}
