package com.pearl.keyboard.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pearl.keyboard.model.KeyType

/**
 * Short, crisp vibrations that mimic the iPhone keyboard's haptic "tick".
 *
 * Uses the platform's predefined [VibrationEffect]s where available — these map to
 * the device's own tuned haptic primitives, which feel far better than a raw
 * fixed-duration buzz and are the closest Android analogue to Apple's Taptic ticks.
 */
class HapticFeedback(context: Context) {

    var enabled: Boolean = true

    /** "light" | "medium" | "strong" — scales the predefined effect used. */
    var strength: String = "light"

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            mgr.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    /** Fire the appropriate tick for the given key type. */
    fun perform(type: KeyType) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        // Heavier keys (return/shift/layout switches) get a slightly stronger click; the
        // overall level is scaled by the user's strength preference.
        val special = when (type) {
            KeyType.ENTER, KeyType.SHIFT, KeyType.SYMBOLS,
            KeyType.LETTERS, KeyType.GLOBE, KeyType.EMOJI -> true
            else -> false
        }
        val effectId = when (strength) {
            "strong" -> VibrationEffect.EFFECT_HEAVY_CLICK
            "medium" -> if (special) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
            else -> if (special) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK
        }
        runCatching {
            v.vibrate(VibrationEffect.createPredefined(effectId))
        }
    }
}
