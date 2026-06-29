package com.pearl.keyboard.ime

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.pearl.keyboard.feature.clipboard.ClipboardHistory
import com.pearl.keyboard.feature.clipboard.ClipboardPanelView
import com.pearl.keyboard.feature.emoji.EmojiPanelView
import com.pearl.keyboard.feature.gif.GifPanelView
import com.pearl.keyboard.feature.theme.BackgroundImage
import com.pearl.keyboard.feature.voice.VoiceInputManager
import com.pearl.keyboard.feature.voice.VoicePermissionActivity
import com.pearl.keyboard.input.Dictionary
import com.pearl.keyboard.input.GestureTypingDetector
import com.pearl.keyboard.input.SuggestionEngine
import com.pearl.keyboard.feedback.HapticFeedback
import com.pearl.keyboard.feedback.SoundFeedback
import com.pearl.keyboard.model.KeyType
import com.pearl.keyboard.model.LayoutId
import com.pearl.keyboard.settings.Prefs
import com.pearl.keyboard.settings.SettingsActivity
import com.pearl.keyboard.theme.KeyboardTheme

/**
 * The keyboard. Everything visual lives in [KeyboardContainerView] / [KeyboardView];
 * this class owns *text*: the composing word, autocorrect, predictions, the action
 * key, feature panels, voice and clipboard.
 *
 * Flow: [KeyboardView] reports intent via [KeyboardListener.onAction]; we translate
 * that into InputConnection edits and keep a composing region so words can be
 * corrected/replaced in place — the same mechanism iOS uses for its blue-underline
 * autocorrect.
 */
class IosKeyboardService : InputMethodService(),
    KeyboardListener,
    SuggestionStripView.Listener,
    PanelActions {

    private val prefs by lazy { Prefs(this) }
    private val dictionary by lazy { Dictionary(this) }
    private val suggestionEngine by lazy { SuggestionEngine(dictionary) }
    private val haptic by lazy { HapticFeedback(this) }
    private val sound by lazy { SoundFeedback(this) }
    private val voice by lazy { VoiceInputManager(this) }

    private lateinit var container: KeyboardContainerView

    /** The word currently being composed (letters only). */
    private val composing = StringBuilder()
    private var lastSpaceTime = 0L
    private var lastAutoCorrection: String? = null

    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    // panels (lazy)
    private var emojiPanel: EmojiPanelView? = null
    private var clipboardPanel: ClipboardPanelView? = null
    private var gifPanel: GifPanelView? = null

    // ======================================================================
    // Lifecycle
    // ======================================================================

    override fun onCreate() {
        super.onCreate()
        ClipboardHistory.init(this)
        registerClipboardWatcher()
        // Warm the dictionary off the main thread so the first keypress isn't delayed.
        Thread { runCatching { dictionary.snapshot() } }.start()
    }

    override fun onCreateInputView(): View {
        container = KeyboardContainerView(this)
        container.keyboardView.listener = this
        container.strip.listener = this
        applyTheme()
        return container
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        // Re-read preferences each time so settings changes take effect immediately.
        applyTheme()
        applyBackgroundImage()
        with(container.keyboardView) {
            setHeightScale(prefs.keyboardHeightScale)
            setOneHanded(prefs.oneHanded, OneHandedSide.RIGHT)
            popupPreviewEnabled = prefs.popupPreview
            gestureEnabled = prefs.gestureTyping
            longPressDelayMs = prefs.longPressDelay.toLong()
            setLayout(LayoutId.LETTERS)
        }
        haptic.enabled = prefs.haptics
        sound.enabled = prefs.sound
        sound.volume = prefs.soundVolume
        sound.pack = prefs.soundPack

        composing.clear()
        lastAutoCorrection = null
        container.showKeyboard()
        clearSuggestions()
        updateActionKey(info)
        updateAutoCapShift()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        finishComposing(applyAutocorrect = false)
        voice.stop()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // If our composing region was dropped (user moved the caret / tapped elsewhere),
        // forget the in-progress word so we don't corrupt later edits.
        if (composing.isNotEmpty() && candidatesStart == -1) {
            composing.setLength(0)
            clearSuggestions()
        }
    }

    override fun onDestroy() {
        unregisterClipboardWatcher()
        sound.release()
        voice.destroy()
        super.onDestroy()
    }

    // ======================================================================
    // KeyboardListener — low-level key events from the view
    // ======================================================================

    override fun onKeyDownFeedback(type: KeyType) {
        haptic.perform(type)
        sound.play(type)
    }

    override fun onGesturePreview(points: List<PointF>) {
        if (!prefs.gestureTyping) return
        val centers = container.keyboardView.letterKeyCenters()
        val word = GestureTypingDetector.decode(points, centers, dictionary) ?: return
        // Live guess only — not committed. The strip is refreshed when the swipe ends.
        container.strip.setSuggestions(listOf(word), 0)
    }

    override fun onAction(action: KeyAction) {
        when (action) {
            is KeyAction.Char -> onChar(action.text)
            KeyAction.Delete -> onDelete()
            KeyAction.Enter -> onEnter()
            KeyAction.Space -> onSpace()
            KeyAction.ShiftChanged -> { /* purely visual; view owns it */ }
            is KeyAction.Switch -> finishComposing(applyAutocorrect = false).also { clearSuggestions() }
            KeyAction.Emoji -> openEmojiPanel()
            KeyAction.Globe -> showImePicker()
            is KeyAction.GesturePath -> onGesture(action)
        }
    }

    // ======================================================================
    // Text editing
    // ======================================================================

    private fun onChar(text: String) {
        val ic = currentInputConnection ?: return
        if (text.length == 1 && text[0].isLetter()) {
            composing.append(text)
            ic.setComposingText(composing, 1)
            updateSuggestions()
        } else {
            // Punctuation/digit ends the current word. Autocorrect first if it terminates a sentence/word.
            val terminates = text.length == 1 && text[0] in WORD_TERMINATORS
            finishComposing(applyAutocorrect = prefs.autocorrect && terminates)
            ic.commitText(text, 1)
            clearSuggestions()
            if (terminates) updateAutoCapShift()
        }
    }

    private fun onDelete() {
        val ic = currentInputConnection ?: return
        val deleted: Boolean
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - 1)
            if (composing.isEmpty()) ic.finishComposingText() else ic.setComposingText(composing, 1)
            updateSuggestions()
            deleted = true
        } else {
            // Delete a whole code point so we don't split a surrogate pair / emoji in half.
            val before = ic.getTextBeforeCursor(2, 0)
            if (before.isNullOrEmpty()) {
                deleted = false
            } else {
                val count = if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
                ic.deleteSurroundingText(count, 0)
                clearSuggestions()
                deleted = true
            }
        }
        // Feedback ONLY when something was actually removed; otherwise stop the repeat
        // immediately so sound/haptics don't keep firing on an empty field.
        if (deleted) {
            haptic.perform(KeyType.DELETE)
            sound.play(KeyType.DELETE)
        } else {
            container.keyboardView.cancelDeleteRepeat()
        }
    }

    private fun onSpace() {
        val ic = currentInputConnection ?: return
        finishComposing(applyAutocorrect = prefs.autocorrect)

        val now = SystemClock.uptimeMillis()
        val before = ic.getTextBeforeCursor(2, 0)
        val doublePeriod = prefs.doubleSpacePeriod &&
            now - lastSpaceTime < DOUBLE_SPACE_MS &&
            before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()

        if (doublePeriod) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
        } else {
            ic.commitText(" ", 1)
        }
        lastSpaceTime = now
        clearSuggestions()
        updateAutoCapShift()
    }

    private fun onEnter() {
        val ic = currentInputConnection ?: return
        finishComposing(applyAutocorrect = false)

        val info = currentInputEditorInfo
        val actionId = info?.let { it.imeOptions and EditorInfo.IME_MASK_ACTION } ?: EditorInfo.IME_ACTION_NONE
        val noAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0

        if (!noAction && actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(actionId)
        } else {
            ic.commitText("\n", 1)
        }
        clearSuggestions()
        updateAutoCapShift()
    }

    private fun onGesture(action: KeyAction.GesturePath) {
        val ic = currentInputConnection ?: return
        val centers = container.keyboardView.letterKeyCenters()
        val word = GestureTypingDetector.decode(action.points, centers, dictionary) ?: return

        finishComposing(applyAutocorrect = false)
        // Auto-insert a space between consecutive words.
        val before = ic.getTextBeforeCursor(1, 0)
        if (before != null && before.isNotEmpty() && before.last().isLetterOrDigit()) ic.commitText(" ", 1)

        val cased = if (container.keyboardView.shiftState != ShiftState.OFF) word.replaceFirstChar { it.uppercase() } else word
        composing.append(cased)
        ic.setComposingText(composing, 1)
        if (container.keyboardView.shiftState == ShiftState.ON) {
            container.keyboardView.shiftState = ShiftState.OFF
        }
        updateSuggestions()
    }

    /** Commit the in-progress word, optionally replacing it with the autocorrect pick. */
    private fun finishComposing(applyAutocorrect: Boolean) {
        val ic = currentInputConnection ?: return
        if (composing.isEmpty()) return
        var word = composing.toString()

        if (applyAutocorrect) {
            val correction = suggestionEngine.suggest(word).autoCorrection
            if (correction != null) word = matchCase(word, correction)
        }
        ic.setComposingText(word, 1)
        ic.finishComposingText()
        dictionary.learn(word)
        composing.setLength(0)
    }

    // ======================================================================
    // Suggestions
    // ======================================================================

    private fun updateSuggestions() {
        if (composing.isEmpty() || (!prefs.predictions && !prefs.autocorrect)) {
            clearSuggestions()
            return
        }
        val result = suggestionEngine.suggest(composing.toString(), 3)
        lastAutoCorrection = if (prefs.autocorrect) result.autoCorrection else null
        val highlight = lastAutoCorrection?.let { result.items.indexOfFirst { s -> s.equals(it, true) } } ?: -1
        container.strip.setSuggestions(result.items, highlight)
    }

    private fun clearSuggestions() {
        lastAutoCorrection = null
        container.strip.clear()
    }

    override fun onSuggestionPicked(text: String, index: Int) {
        val ic = currentInputConnection ?: return
        val chosen = if (composing.isNotEmpty()) matchCase(composing.toString(), text) else text
        if (composing.isNotEmpty()) {
            ic.setComposingText(chosen, 1)
            ic.finishComposingText()
        } else {
            ic.commitText(chosen, 1)
        }
        ic.commitText(" ", 1)
        dictionary.learn(text)
        composing.setLength(0)
        clearSuggestions()
        updateAutoCapShift()
    }

    override fun onToolbarAction(action: SuggestionStripView.Toolbar) {
        when (action) {
            SuggestionStripView.Toolbar.EMOJI -> openEmojiPanel()
            SuggestionStripView.Toolbar.GIF -> openGifPanel()
            SuggestionStripView.Toolbar.CLIPBOARD -> openClipboardPanel()
            SuggestionStripView.Toolbar.VOICE -> startVoice()
            SuggestionStripView.Toolbar.SETTINGS -> openSettings()
        }
    }

    // ======================================================================
    // PanelActions — feature panels insert via these
    // ======================================================================

    override fun commitText(text: String) {
        finishComposing(applyAutocorrect = false)
        currentInputConnection?.commitText(text, 1)
    }

    override fun backspace() = onDelete()

    override fun closePanel() {
        container.showKeyboard()
        clearSuggestions()
    }

    override fun commitMedia(uri: Uri, mimeType: String, fallbackText: String) {
        val ic = currentInputConnection ?: return
        finishComposing(applyAutocorrect = false)
        val editorInfo = currentInputEditorInfo
        val supported = editorInfo != null &&
            EditorInfoCompat.getContentMimeTypes(editorInfo).any { ClipDescription.compareMimeTypes(mimeType, it) }
        val committed = if (supported && editorInfo != null) {
            val info = InputContentInfoCompat(uri, ClipDescription("GIF", arrayOf(mimeType)), null)
            runCatching {
                InputConnectionCompat.commitContent(
                    ic, editorInfo, info,
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null
                )
            }.getOrDefault(false)
        } else false

        if (!committed) {
            // Fallback: many fields don't accept inline media — copy + paste the link.
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.setPrimaryClip(ClipData.newPlainText("GIF", fallbackText))
            ic.commitText(fallbackText, 1)
            toast("GIF link inserted (this field may not support inline GIFs)")
        }
    }

    // ======================================================================
    // Feature panels
    // ======================================================================

    private fun openEmojiPanel() {
        finishComposing(applyAutocorrect = false)
        val panel = emojiPanel ?: EmojiPanelView(this).also { emojiPanel = it }
        panel.actions = this
        panel.setTheme(currentTheme())
        container.showPanel(panel)
    }

    private fun openClipboardPanel() {
        if (!prefs.clipboardEnabled) {
            toast("Clipboard history is turned off in settings")
            return
        }
        finishComposing(applyAutocorrect = false)
        val panel = clipboardPanel ?: ClipboardPanelView(this).also { clipboardPanel = it }
        panel.actions = this
        panel.setTheme(currentTheme())
        panel.refresh()
        container.showPanel(panel)
    }

    private fun openGifPanel() {
        if (!prefs.gifEnabled) {
            toast("Enable GIF search in settings (needs a Tenor API key)")
            return
        }
        finishComposing(applyAutocorrect = false)
        val panel = gifPanel ?: GifPanelView(this).also { gifPanel = it }
        panel.actions = this
        panel.setTheme(currentTheme())
        container.showPanel(panel)
    }

    // ======================================================================
    // Voice typing
    // ======================================================================

    private fun startVoice() {
        if (!prefs.voiceEnabled) {
            toast("Voice typing is turned off in settings")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // An IME can't request a runtime permission directly — bounce through an activity.
            startActivity(Intent(this, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (!voice.isAvailable()) {
            toast("No speech recognition service available")
            return
        }
        voice.start(object : VoiceInputManager.Callback {
            override fun onText(text: String) {
                finishComposing(applyAutocorrect = false)
                currentInputConnection?.commitText(text, 1)
            }

            override fun onError(message: String) = toast(message)
            override fun onStateChanged(listening: Boolean) { /* hook for a UI indicator */ }
        })
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    private fun currentTheme(): KeyboardTheme =
        KeyboardTheme.build(
            this, prefs.themePreset, prefs.themeMode, prefs.keyBorders,
            prefs.accentColor, prefs.keyOpacity
        )

    private fun applyTheme() {
        if (::container.isInitialized) container.setTheme(currentTheme())
    }

    /** Load the custom background image (off the main thread) and apply it, or clear it. */
    private fun applyBackgroundImage() {
        if (!::container.isInitialized) return
        val path = prefs.bgImagePath
        if (path.isEmpty()) {
            container.keyboardView.setBackgroundImage(null, 0)
            return
        }
        val blur = prefs.bgBlur
        val brightness = prefs.bgBrightness
        val dim = prefs.bgDim
        Thread {
            val bmp = runCatching { BackgroundImage.processed(this, path, blur, brightness) }.getOrNull()
            container.keyboardView.post { container.keyboardView.setBackgroundImage(bmp, dim) }
        }.start()
    }

    private fun updateActionKey(info: EditorInfo?) {
        val actionId = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val custom = info?.actionLabel?.toString()
        val (label, accent) = when {
            custom != null -> custom to true
            actionId == EditorInfo.IME_ACTION_GO -> "Go" to true
            actionId == EditorInfo.IME_ACTION_SEARCH -> "Search" to true
            actionId == EditorInfo.IME_ACTION_SEND -> "Send" to true
            actionId == EditorInfo.IME_ACTION_DONE -> "Done" to true
            actionId == EditorInfo.IME_ACTION_NEXT -> "Next" to false
            else -> "return" to false
        }
        container.keyboardView.setActionKey(label, accent)
    }

    /** Set the shift highlight from the editor's caps mode (sentence start, etc.). */
    private fun updateAutoCapShift() {
        val view = container.keyboardView
        if (view.shiftState == ShiftState.LOCKED) return
        if (!prefs.autoCap) {
            view.shiftState = ShiftState.OFF
            return
        }
        val ic = currentInputConnection
        val info = currentInputEditorInfo
        val caps = if (ic != null && info != null) ic.getCursorCapsMode(info.inputType) else 0
        view.shiftState = if (caps != 0) ShiftState.ON else ShiftState.OFF
    }

    /** Preserve the typed word's capitalization when swapping in a suggestion/correction. */
    private fun matchCase(original: String, replacement: String): String = when {
        original.isEmpty() -> replacement
        original.all { it.isUpperCase() } && original.length > 1 -> replacement.uppercase()
        original[0].isUpperCase() -> replacement.replaceFirstChar { it.uppercase() }
        else -> replacement
    }

    private fun showImePicker() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun registerClipboardWatcher() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val l = ClipboardManager.OnPrimaryClipChangedListener {
            if (!prefs.clipboardEnabled) return@OnPrimaryClipChangedListener
            val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
            if (clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this).toString().takeIf { it.isNotBlank() }?.let {
                    ClipboardHistory.add(it)
                }
            }
        }
        cm.addPrimaryClipChangedListener(l)
        clipboardListener = l
    }

    private fun unregisterClipboardWatcher() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboardListener?.let { cm.removePrimaryClipChangedListener(it) }
        clipboardListener = null
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val DOUBLE_SPACE_MS = 350L
        private val WORD_TERMINATORS = charArrayOf('.', ',', '!', '?', ';', ':').toHashSet()
    }
}
