package com.pearl.keyboard.ime

import android.graphics.PointF
import com.pearl.keyboard.model.KeyType
import com.pearl.keyboard.model.LayoutId

/** Visual shift state, mirrored between the view and the IME service. */
enum class ShiftState { OFF, ON, LOCKED }

/**
 * Everything the [KeyboardView] can ask the host (the IME service) to do.
 * The view handles its own *visual* state (which page, shift highlight, popups);
 * the service handles *text* (composing region, autocorrect, suggestions, panels).
 */
sealed interface KeyAction {
    /** A character was committed, already in the correct case. */
    data class Char(val text: String) : KeyAction
    object Delete : KeyAction
    object Enter : KeyAction
    object Space : KeyAction
    /** Shift highlight changed; read [KeyboardView.shiftState] if needed. */
    object ShiftChanged : KeyAction
    /** The visible page changed (the view has already switched). */
    data class Switch(val layout: LayoutId) : KeyAction
    object Emoji : KeyAction
    object Globe : KeyAction
    /** A glide/swipe gesture finished; raw path in view coordinates. */
    data class GesturePath(val points: List<PointF>) : KeyAction
}

interface KeyboardListener {
    fun onAction(action: KeyAction)
    /** Fired the instant a key is touched, for low-latency haptic + sound. */
    fun onKeyDownFeedback(type: KeyType)
    /** Throttled live prediction while the user is mid-swipe (does not commit). */
    fun onGesturePreview(points: List<PointF>)
}
