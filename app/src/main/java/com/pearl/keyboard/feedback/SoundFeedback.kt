package com.pearl.keyboard.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.pearl.keyboard.model.KeyType

/**
 * Keypress sounds.
 *
 * DEFAULT (legally safe): plays the operating system's own keypress effects via
 * [AudioManager.playSoundEffect]. These are the stock Android click sounds — no
 * copyrighted Apple assets involved.
 *
 * OPTIONAL: drop your own short samples into res/raw and they are picked up
 * automatically (no code change). Expected file names (any audio format Android
 * supports, e.g. .ogg / .wav):
 *   - keypress_standard   (letters / symbols)
 *   - keypress_delete
 *   - keypress_return
 *   - keypress_spacebar
 * Record or synthesise iPhone-like ticks yourself; do not copy Apple's files.
 */
class SoundFeedback(context: Context) {

    var enabled: Boolean = true

    /** 0f..1f scalar applied to every sound. */
    var volume: Float = 0.4f

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var soundPool: SoundPool? = null
    private val custom = HashMap<Bucket, Int>()

    private enum class Bucket { STANDARD, DELETE, RETURN, SPACE }

    init {
        val pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()

        load(pool, context, "keypress_standard", Bucket.STANDARD)
        load(pool, context, "keypress_delete", Bucket.DELETE)
        load(pool, context, "keypress_return", Bucket.RETURN)
        load(pool, context, "keypress_spacebar", Bucket.SPACE)

        // If the user didn't ship any custom samples, fall back to system effects.
        soundPool = if (custom.isEmpty()) {
            pool.release()
            null
        } else {
            pool
        }
    }

    private fun load(pool: SoundPool, context: Context, name: String, bucket: Bucket) {
        val resId = context.resources.getIdentifier(name, "raw", context.packageName)
        if (resId != 0) custom[bucket] = pool.load(context, resId, 1)
    }

    fun play(type: KeyType) {
        if (!enabled) return
        val bucket = when (type) {
            KeyType.DELETE -> Bucket.DELETE
            KeyType.ENTER -> Bucket.RETURN
            KeyType.SPACE -> Bucket.SPACE
            else -> Bucket.STANDARD
        }

        val pool = soundPool
        val customId = custom[bucket] ?: custom[Bucket.STANDARD]
        if (pool != null && customId != null) {
            pool.play(customId, volume, volume, 1, 0, 1f)
        } else {
            audioManager.playSoundEffect(systemEffect(type), volume)
        }
    }

    private fun systemEffect(type: KeyType): Int = when (type) {
        KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
        KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
        KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
        else -> AudioManager.FX_KEYPRESS_STANDARD
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
