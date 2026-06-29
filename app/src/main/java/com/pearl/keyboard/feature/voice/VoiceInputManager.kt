package com.pearl.keyboard.feature.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Thin wrapper around the platform [SpeechRecognizer] for dictation.
 *
 * This uses whatever recognition service the device provides (often Google's),
 * including its on-device model where available. Must be driven from the main thread
 * — the IME callbacks already are.
 */
class VoiceInputManager(context: Context) {

    interface Callback {
        fun onText(text: String)
        fun onError(message: String)
        fun onStateChanged(listening: Boolean)
    }

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var callback: Callback? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start(cb: Callback) {
        stop()
        callback = cb
        val r = SpeechRecognizer.createSpeechRecognizer(appContext)
        r.setRecognitionListener(listener)
        recognizer = r
        r.startListening(buildIntent())
        cb.onStateChanged(true)
    }

    fun stop() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
        callback?.onStateChanged(false)
    }

    fun destroy() = stop()

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle) {
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) callback?.onText(text)
            stop()
        }

        override fun onError(error: Int) {
            callback?.onError(errorMessage(error))
            stop()
        }

        override fun onEndOfSpeech() { callback?.onStateChanged(false) }

        // Unused but required overrides.
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        else -> "Voice input error ($code)"
    }
}
