package com.pearl.keyboard.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.pearl.keyboard.model.KeyType
import java.io.File

/**
 * Keypress sounds — an ORIGINAL, synthesised sound pack (see [SoundSynth]). We no longer
 * fall back to Android's system click effects; the default "iphone" voice recreates the
 * soft, brief tap feel of a modern phone keyboard without copying any copyrighted audio.
 *
 * Precedence: if the user drops their own samples into res/raw (keypress_standard /
 * keypress_delete / keypress_return / keypress_spacebar) those override the synthesised
 * pack. "silent" plays nothing.
 *
 * Public API (enabled / volume / pack / play / release) is unchanged, so the IME service
 * is unaffected.
 */
class SoundFeedback(context: Context) {

    var enabled: Boolean = true

    /** 0f..1f scalar applied to every sound. */
    var volume: Float = 0.4f

    /** Selected pack id: iphone | android | gboard | mechanical | soft | retro | silent. */
    var pack: String = "iphone"
        set(value) {
            if (field != value) {
                field = value
                rebuild()
            }
        }

    private val appContext = context.applicationContext
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val soundIds = HashMap<Bucket, Int>()

    private enum class Bucket(val kind: String) {
        STANDARD("standard"), DELETE("delete"), RETURN("return"), SPACE("space")
    }

    init {
        rebuild()
    }

    /** (Re)load the sounds for the current [pack]. Cheap: four short synthesised clips. */
    private fun rebuild() {
        soundIds.values.forEach { soundPool.unload(it) }
        soundIds.clear()
        if (pack == "silent") return
        if (!loadCustomRaw()) loadSynth()
    }

    private fun loadCustomRaw(): Boolean {
        val names = mapOf(
            Bucket.STANDARD to "keypress_standard",
            Bucket.DELETE to "keypress_delete",
            Bucket.RETURN to "keypress_return",
            Bucket.SPACE to "keypress_spacebar"
        )
        var any = false
        for ((bucket, name) in names) {
            val resId = appContext.resources.getIdentifier(name, "raw", appContext.packageName)
            if (resId != 0) {
                soundIds[bucket] = soundPool.load(appContext, resId, 1)
                any = true
            }
        }
        return any
    }

    private fun loadSynth() {
        for (bucket in Bucket.values()) {
            runCatching {
                val wav = SoundSynth.wav(pack, bucket.kind)
                val file = File(appContext.cacheDir, "snd_${pack}_${bucket.kind}.wav")
                file.writeBytes(wav)
                soundIds[bucket] = soundPool.load(file.absolutePath, 1)
            }
        }
    }

    fun play(type: KeyType) {
        if (!enabled || pack == "silent") return
        val bucket = when (type) {
            KeyType.DELETE -> Bucket.DELETE
            KeyType.ENTER -> Bucket.RETURN
            KeyType.SPACE -> Bucket.SPACE
            else -> Bucket.STANDARD
        }
        val id = soundIds[bucket] ?: soundIds[Bucket.STANDARD] ?: return
        soundPool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
        soundIds.clear()
    }
}
