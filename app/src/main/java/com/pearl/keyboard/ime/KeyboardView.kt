package com.pearl.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.pearl.keyboard.model.Key
import com.pearl.keyboard.model.KeyIcon
import com.pearl.keyboard.model.KeyType
import com.pearl.keyboard.model.KeyboardLayout
import com.pearl.keyboard.model.LayoutId
import com.pearl.keyboard.model.Layouts
import com.pearl.keyboard.theme.KeyboardTheme
import com.pearl.keyboard.util.dp
import kotlin.math.max
import kotlin.math.min

enum class OneHandedSide { LEFT, RIGHT }

/**
 * The custom-drawn key field. Owns only *visual* state (current page, shift
 * highlight, pressed keys, pop-ups, the in-progress glide path) and reports user
 * intent to a [KeyboardListener]; the IME service does everything text-related.
 *
 * Geometry is recomputed from the view width so the same layout fits any phone,
 * tablet or foldable. Each row is centred, which reproduces the inset look of the
 * iOS home row and shift/return rows.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var listener: KeyboardListener? = null

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    // ---- configurable from preferences ------------------------------------
    var popupPreviewEnabled = true
    var gestureEnabled = true
    private var oneHanded = false
    private var oneHandedSide = OneHandedSide.RIGHT
    private var heightScale = 1f
    private var actionLabel = "return"
    private var actionAccent = false

    private var layout: KeyboardLayout = Layouts.letters
    private var theme: KeyboardTheme = KeyboardTheme.light(false)

    // ---- metrics (px) -----------------------------------------------------
    private val rowHeightBase = context.resources.getDimension(com.pearl.keyboard.R.dimen.key_row_height)
    private val hGap = context.resources.getDimension(com.pearl.keyboard.R.dimen.key_gap_horizontal)
    private val vGap = context.resources.getDimension(com.pearl.keyboard.R.dimen.key_gap_vertical)
    private val corner = context.resources.getDimension(com.pearl.keyboard.R.dimen.key_corner_radius)
    private val sidePad = context.resources.getDimension(com.pearl.keyboard.R.dimen.keyboard_side_padding)
    private val topPad = context.resources.getDimension(com.pearl.keyboard.R.dimen.keyboard_top_padding)
    private val bottomPad = context.resources.getDimension(com.pearl.keyboard.R.dimen.keyboard_bottom_padding)
    private val letterTextSize = context.resources.getDimension(com.pearl.keyboard.R.dimen.key_letter_text_size)
    private val specialTextSize = context.resources.getDimension(com.pearl.keyboard.R.dimen.key_special_text_size)
    private val maxContentWidth = context.dp(720f)   // cap key width on tablets
    private var landscape = false

    // ---- computed layout --------------------------------------------------
    private class PositionedKey(val key: Key, val rect: RectF, val row: Int)
    private val positioned = ArrayList<PositionedKey>()

    // ---- paints -----------------------------------------------------------
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val keyFont: Typeface =
        runCatching { Typeface.createFromAsset(context.assets, "fonts/keyboard.ttf") }
            .getOrDefault(Typeface.create("sans-serif", Typeface.NORMAL))

    // ---- touch / gesture state -------------------------------------------
    private val pointerKeys = SparseArray<PositionedKey>()
    private val pressedKeys = HashSet<PositionedKey>()
    private val handler = Handler(Looper.getMainLooper())
    private val popup = KeyPreviewPopup(context)

    private var previewPointerId = -1
    private var deletePointerId = -1
    private var alternatesPointerId = -1

    private var gesturePointerId = -1
    private var gestureCandidateId = -1
    private var gestureMaybe = false
    private val gestureStart = PointF()
    private val gesturePoints = ArrayList<PointF>()
    private val gesturePath = Path()
    private val gestureSlop = ViewConfiguration.get(context).scaledTouchSlop * 1.6f

    private var lastShiftTap = 0L

    // long-press alternates bookkeeping
    private var longPressId = -1
    private var longPressKey: PositionedKey? = null
    private var alternatesItems: List<String> = emptyList()
    private var alternatesLeftView = 0f
    private var alternatesCellW = 0f

    init {
        isFocusable = false
        popup.setTheme(theme)
    }

    // ======================================================================
    // Public configuration API (called by the IME service)
    // ======================================================================

    fun setTheme(t: KeyboardTheme) {
        theme = t
        popup.setTheme(t)
        invalidate()
    }

    fun setLayout(id: LayoutId) {
        if (layout.id == id) return
        layout = Layouts.byId(id)
        resetTransientState()
        // Page switches keep the same view size, so onSizeChanged won't fire —
        // recompute the key rectangles for the new page explicitly.
        recompute()
        requestLayout()
        invalidate()
    }

    fun currentLayoutId(): LayoutId = layout.id

    fun setActionKey(label: String, accent: Boolean) {
        actionLabel = label
        actionAccent = accent
        invalidate()
    }

    fun setOneHanded(enabled: Boolean, side: OneHandedSide) {
        oneHanded = enabled
        oneHandedSide = side
        recompute()
        invalidate()
    }

    fun setHeightScale(scale: Float) {
        heightScale = scale
        requestLayout()
    }

    /** Char → centre point (view coords) of every letter key — used by the glide decoder. */
    fun letterKeyCenters(): Map<Char, PointF> {
        val map = HashMap<Char, PointF>()
        if (layout.id != LayoutId.LETTERS) return map
        for (pk in positioned) {
            if (pk.key.type == KeyType.CHAR && pk.key.label.length == 1) {
                map[pk.key.label[0]] = PointF(pk.rect.centerX(), pk.rect.centerY())
            }
        }
        return map
    }

    // ======================================================================
    // Measurement & layout
    // ======================================================================

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = layout.rows.size
        val height = (topPad + rows * rowHeight() + (rows - 1) * vGap + bottomPad).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recompute()
    }

    private fun rowHeight(): Float = rowHeightBase * heightScale * (if (landscape) 0.82f else 1f)

    /** Recompute every key rectangle for the current width. */
    private fun recompute() {
        positioned.clear()
        if (width == 0) return

        var left = sidePad
        var right = width - sidePad
        if (right - left > maxContentWidth) {
            val mid = (left + right) / 2f
            left = mid - maxContentWidth / 2f
            right = mid + maxContentWidth / 2f
        }
        if (oneHanded) {
            val ohw = (right - left) * 0.86f
            if (oneHandedSide == OneHandedSide.RIGHT) left = right - ohw else right = left + ohw
        }
        val contentW = right - left

        // Reference row = the widest, so all rows share one key unit and narrower
        // rows end up centred (the iOS inset look).
        val refRow = layout.rows.maxByOrNull { row -> row.sumOf { it.widthWeight.toDouble() } }!!
        val refWeight = refRow.map { it.widthWeight }.sum()
        val unit = (contentW - (refRow.size - 1) * hGap) / refWeight

        val rh = rowHeight()
        for (r in layout.rows.indices) {
            val row = layout.rows[r]
            val rowWeight = row.map { it.widthWeight }.sum()
            val rowW = rowWeight * unit + (row.size - 1) * hGap
            var x = left + (contentW - rowW) / 2f
            val y = topPad + r * (rh + vGap)
            for (key in row) {
                val kw = key.widthWeight * unit
                positioned.add(PositionedKey(key, RectF(x, y, x + kw, y + rh), r))
                x += kw + hGap
            }
        }
    }

    private fun keyAt(x: Float, y: Float): PositionedKey? =
        positioned.firstOrNull { !it.key.isSpacer && it.rect.contains(x, y) }

    // ======================================================================
    // Drawing
    // ======================================================================

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        for (pk in positioned) {
            if (pk.key.isSpacer) continue
            drawKey(canvas, pk)
        }
        if (gesturePointerId != -1 && gesturePoints.size > 1) drawGestureTrail(canvas)
    }

    private fun drawKey(canvas: Canvas, pk: PositionedKey) {
        val rect = pk.rect
        val key = pk.key
        val pressed = pressedKeys.contains(pk)
        val shiftActive = key.type == KeyType.SHIFT && shiftState != ShiftState.OFF
        val enterAccent = key.type == KeyType.ENTER && actionAccent

        val bg = when {
            enterAccent -> if (pressed) blend(theme.accent, Color.BLACK, 0.12f) else theme.accent
            shiftActive -> theme.functionKeyBgPressed
            key.isFunctionKey -> if (pressed) theme.functionKeyBgPressed else theme.functionKeyBg
            else -> if (pressed) theme.keyBgPressed else theme.keyBg // CHAR + SPACE
        }

        // Subtle bottom drop-shadow (iOS keys sit slightly above the surface).
        shadowPaint.color = theme.keyShadow
        canvas.drawRoundRect(
            rect.left, rect.top + context.dp(1.2f), rect.right, rect.bottom + context.dp(1.2f),
            corner, corner, shadowPaint
        )

        keyPaint.color = bg
        canvas.drawRoundRect(rect, corner, corner, keyPaint)

        if (theme.keyBorders) {
            borderPaint.color = theme.keyBorder
            borderPaint.strokeWidth = context.dp(1f)
            canvas.drawRoundRect(rect, corner, corner, borderPaint)
        }

        val fg = when {
            enterAccent -> theme.accentText
            key.isFunctionKey -> theme.functionKeyText
            else -> theme.keyText
        }

        when (key.icon) {
            KeyIcon.SHIFT -> drawShift(canvas, rect, fg)
            KeyIcon.DELETE -> drawDelete(canvas, rect, fg)
            KeyIcon.GLOBE -> drawGlobe(canvas, rect, fg)
            KeyIcon.EMOJI -> drawEmojiFace(canvas, rect, fg)
            else -> drawKeyText(canvas, pk, fg)
        }
    }

    private fun drawKeyText(canvas: Canvas, pk: PositionedKey, color: Int) {
        val key = pk.key
        if (key.type == KeyType.SPACE) return // iOS spacebar is blank

        val label = when (key.type) {
            KeyType.ENTER -> actionLabel
            KeyType.CHAR -> if (layout.id == LayoutId.LETTERS && shiftState != ShiftState.OFF)
                key.label.uppercase() else key.label
            else -> key.label
        }
        textPaint.typeface = keyFont
        textPaint.color = color
        textPaint.textSize = when (key.type) {
            KeyType.CHAR -> letterTextSize
            else -> specialTextSize
        }
        val fm = textPaint.fontMetrics
        val baseline = pk.rect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, pk.rect.centerX(), baseline, textPaint)
    }

    private fun drawGestureTrail(canvas: Canvas) {
        gesturePath.reset()
        gesturePath.moveTo(gesturePoints[0].x, gesturePoints[0].y)
        for (i in 1 until gesturePoints.size) gesturePath.lineTo(gesturePoints[i].x, gesturePoints[i].y)
        trailPaint.color = theme.gestureTrail
        trailPaint.strokeWidth = context.dp(9f)
        canvas.drawPath(gesturePath, trailPaint)
    }

    // ---- vector glyphs for special keys -----------------------------------

    private fun drawShift(canvas: Canvas, rect: RectF, color: Int) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val s = min(rect.width(), rect.height()) * 0.30f
        iconPaint.color = color
        iconPaint.style = Paint.Style.FILL
        val p = Path().apply {
            moveTo(cx, cy - s)               // arrow tip
            lineTo(cx - s, cy)               // left base
            lineTo(cx - s * 0.45f, cy)
            lineTo(cx - s * 0.45f, cy + s * 0.75f) // stem
            lineTo(cx + s * 0.45f, cy + s * 0.75f)
            lineTo(cx + s * 0.45f, cy)
            lineTo(cx + s, cy)               // right base
            close()
        }
        canvas.drawPath(p, iconPaint)
        if (shiftState == ShiftState.LOCKED) {
            // Caps-lock bar under the arrow.
            canvas.drawRect(cx - s * 0.6f, cy + s * 0.95f, cx + s * 0.6f, cy + s * 1.2f, iconPaint)
        }
    }

    private fun drawDelete(canvas: Canvas, rect: RectF, color: Int) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val w = rect.width() * 0.26f
        val h = rect.height() * 0.20f
        iconPaint.color = color
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = context.dp(1.7f)
        val body = Path().apply {
            moveTo(cx - w, cy)
            lineTo(cx - w * 0.45f, cy - h)
            lineTo(cx + w, cy - h)
            lineTo(cx + w, cy + h)
            lineTo(cx - w * 0.45f, cy + h)
            close()
        }
        canvas.drawPath(body, iconPaint)
        // the "x"
        val ax = cx + w * 0.15f
        val arm = h * 0.5f
        canvas.drawLine(ax - arm, cy - arm, ax + arm, cy + arm, iconPaint)
        canvas.drawLine(ax + arm, cy - arm, ax - arm, cy + arm, iconPaint)
    }

    private fun drawGlobe(canvas: Canvas, rect: RectF, color: Int) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.28f
        iconPaint.color = color
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = context.dp(1.5f)
        canvas.drawCircle(cx, cy, r, iconPaint)
        canvas.drawOval(cx - r * 0.55f, cy - r, cx + r * 0.55f, cy + r, iconPaint) // meridians
        canvas.drawLine(cx - r, cy, cx + r, cy, iconPaint)                          // equator
        canvas.drawLine(cx - r * 0.86f, cy - r * 0.5f, cx + r * 0.86f, cy - r * 0.5f, iconPaint)
        canvas.drawLine(cx - r * 0.86f, cy + r * 0.5f, cx + r * 0.86f, cy + r * 0.5f, iconPaint)
    }

    private fun drawEmojiFace(canvas: Canvas, rect: RectF, color: Int) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.30f
        iconPaint.color = color
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = context.dp(1.5f)
        canvas.drawCircle(cx, cy, r, iconPaint)
        iconPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx - r * 0.38f, cy - r * 0.25f, context.dp(1.6f), iconPaint)
        canvas.drawCircle(cx + r * 0.38f, cy - r * 0.25f, context.dp(1.6f), iconPaint)
        iconPaint.style = Paint.Style.STROKE
        val smile = RectF(cx - r * 0.5f, cy - r * 0.1f, cx + r * 0.5f, cy + r * 0.55f)
        canvas.drawArc(smile, 20f, 140f, false, iconPaint)
    }

    // ======================================================================
    // Touch handling
    // ======================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                handleDown(event, event.actionIndex)

            MotionEvent.ACTION_MOVE ->
                for (i in 0 until event.pointerCount) handleMove(event, i)

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                handleUp(event, event.actionIndex)

            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun handleDown(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        val pk = keyAt(x, y) ?: return
        pointerKeys.put(id, pk)
        pressedKeys.add(pk)
        invalidate()
        listener?.onKeyDownFeedback(pk.key.type)

        when (pk.key.type) {
            KeyType.DELETE -> {
                deletePointerId = id
                listener?.onAction(KeyAction.Delete)
                handler.postDelayed(deleteRunnable, REPEAT_DELAY)
            }
            KeyType.CHAR -> {
                if (showPreview()) showCharPreview(id, pk)
                if (pk.key.popup.isNotEmpty()) scheduleLongPress(id, pk)
                if (gestureEnabled && pointerKeys.size() == 1 && layout.id == LayoutId.LETTERS) {
                    gestureCandidateId = id
                    gestureStart.set(x, y)
                    gestureMaybe = true
                }
            }
            else -> { /* commit on up */ }
        }
    }

    private fun handleMove(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)

        if (id == gesturePointerId) {
            gesturePoints.add(PointF(x, y))
            invalidate()
            return
        }
        if (id == alternatesPointerId) {
            updateAlternateSelection(x)
            return
        }
        val pk = pointerKeys.get(id) ?: return

        // Promote to a glide gesture if the finger travels far enough from a letter key.
        if (gestureMaybe && id == gestureCandidateId && layout.id == LayoutId.LETTERS) {
            val dx = x - gestureStart.x
            val dy = y - gestureStart.y
            if (dx * dx + dy * dy > gestureSlop * gestureSlop) {
                beginGesture(id)
                return
            }
        }

        // Slide to an adjacent letter to correct aim (iOS lets you do this).
        if (pk.key.type == KeyType.CHAR) {
            val nk = keyAt(x, y)
            if (nk != null && nk !== pk && nk.key.type == KeyType.CHAR) {
                pressedKeys.remove(pk)
                pointerKeys.put(id, nk)
                pressedKeys.add(nk)
                cancelLongPress(id)
                if (nk.key.popup.isNotEmpty()) scheduleLongPress(id, nk)
                if (showPreview()) showCharPreview(id, nk)
                invalidate()
            }
        }
    }

    private fun handleUp(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        cancelLongPress(id)

        if (id == gesturePointerId) {
            endGesture()
            cleanupPointer(id)
            return
        }
        if (id == deletePointerId) {
            handler.removeCallbacks(deleteRunnable)
            deletePointerId = -1
            cleanupPointer(id)
            return
        }
        if (id == alternatesPointerId) {
            commitAlternate()
            dismissAlternates()
            cleanupPointer(id)
            return
        }
        val pk = pointerKeys.get(id)
        cleanupPointer(id)
        if (pk != null) commitKey(pk.key)
    }

    private fun cleanupPointer(id: Int) {
        val pk = pointerKeys.get(id)
        pointerKeys.remove(id)
        if (pk != null) pressedKeys.remove(pk)
        if (id == gestureCandidateId) {
            gestureMaybe = false
            gestureCandidateId = -1
        }
        if (id == previewPointerId) {
            previewPointerId = -1
            popup.dismiss()
        }
        invalidate()
    }

    private fun cancelAll() {
        handler.removeCallbacksAndMessages(null)
        pointerKeys.clear()
        pressedKeys.clear()
        deletePointerId = -1
        alternatesPointerId = -1
        previewPointerId = -1
        gesturePointerId = -1
        gestureCandidateId = -1
        gestureMaybe = false
        gesturePoints.clear()
        popup.dismiss()
        invalidate()
    }

    private fun commitKey(key: Key) {
        when (key.type) {
            KeyType.CHAR -> {
                listener?.onAction(KeyAction.Char(casedOutput(key)))
                if (shiftState == ShiftState.ON) {
                    shiftState = ShiftState.OFF
                    listener?.onAction(KeyAction.ShiftChanged)
                }
            }
            KeyType.SPACE -> listener?.onAction(KeyAction.Space)
            KeyType.ENTER -> listener?.onAction(KeyAction.Enter)
            KeyType.SHIFT -> handleShiftTap()
            KeyType.SYMBOLS -> key.targetLayout?.let { setLayout(it); listener?.onAction(KeyAction.Switch(it)) }
            KeyType.LETTERS -> { setLayout(LayoutId.LETTERS); listener?.onAction(KeyAction.Switch(LayoutId.LETTERS)) }
            KeyType.EMOJI -> listener?.onAction(KeyAction.Emoji)
            KeyType.GLOBE -> listener?.onAction(KeyAction.Globe)
            else -> {}
        }
    }

    private fun casedOutput(key: Key): String =
        if (layout.id == LayoutId.LETTERS && shiftState != ShiftState.OFF) key.text.uppercase() else key.text

    private fun handleShiftTap() {
        val now = SystemClock.uptimeMillis()
        shiftState = if (now - lastShiftTap < DOUBLE_TAP_MS) {
            ShiftState.LOCKED
        } else {
            if (shiftState == ShiftState.OFF) ShiftState.ON else ShiftState.OFF
        }
        lastShiftTap = now
        listener?.onAction(KeyAction.ShiftChanged)
    }

    private fun showPreview(): Boolean = popupPreviewEnabled && !landscape

    // ---- key preview ------------------------------------------------------

    private fun showCharPreview(id: Int, pk: PositionedKey) {
        val rect = pk.rect
        val kw = rect.width()
        val kh = rect.height()
        val w = max(kw * 1.5f, context.dp(40f))
        val bodyH = kh * 1.18f
        val h = bodyH + kh * 0.95f
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val screenW = resources.displayMetrics.widthPixels
        var xWin = loc[0] + rect.centerX() - w / 2f
        xWin = xWin.coerceIn(0f, (screenW - w).coerceAtLeast(0f))
        val yWin = loc[1] + rect.centerY() - h
        popup.showChar(this, xWin.toInt(), yWin.toInt(), w.toInt(), h.toInt(), bodyH, kw * 0.5f, casedOutput(pk.key))
        previewPointerId = id
    }

    // ---- long-press alternates --------------------------------------------

    private fun scheduleLongPress(id: Int, pk: PositionedKey) {
        cancelLongPress(id)
        longPressId = id
        longPressKey = pk
        handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
    }

    private fun cancelLongPress(id: Int) {
        if (longPressId == id) {
            handler.removeCallbacks(longPressRunnable)
            longPressId = -1
            longPressKey = null
        }
    }

    private val longPressRunnable = Runnable {
        val pk = longPressKey ?: return@Runnable
        if (pointerKeys.get(longPressId) === pk) openAlternates(longPressId, pk)
    }

    private fun openAlternates(id: Int, pk: PositionedKey) {
        val shifted = layout.id == LayoutId.LETTERS && shiftState != ShiftState.OFF
        alternatesItems = (listOf(pk.key.text) + pk.key.popup)
            .map { if (shifted) it.uppercase() else it }
        alternatesPointerId = id

        val cellW = max(pk.rect.width() * 1.15f, context.dp(38f))
        val totalW = cellW * alternatesItems.size
        val bodyH = pk.rect.height() * 1.15f
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val screenW = resources.displayMetrics.widthPixels
        val desiredLeftWin = loc[0] + pk.rect.centerX() - totalW / 2f
        val leftWin = desiredLeftWin.coerceIn(0f, (screenW - totalW).coerceAtLeast(0f))
        alternatesLeftView = leftWin - loc[0]
        alternatesCellW = cellW
        val yWin = loc[1] + pk.rect.top - bodyH - context.dp(8f)

        popup.dismiss() // dismiss the single-char preview first
        popup.showAlternates(this, leftWin.toInt(), yWin.toInt(), totalW.toInt(), bodyH.toInt(), bodyH, alternatesItems, 0, cellW)
        previewPointerId = id
    }

    private fun updateAlternateSelection(viewX: Float) {
        if (alternatesItems.isEmpty()) return
        val idx = ((viewX - alternatesLeftView) / alternatesCellW).toInt()
            .coerceIn(0, alternatesItems.size - 1)
        popup.setSelected(idx)
    }

    private fun commitAlternate() {
        val sel = popup.selectedItem ?: return
        listener?.onAction(KeyAction.Char(sel))
        if (shiftState == ShiftState.ON) {
            shiftState = ShiftState.OFF
            listener?.onAction(KeyAction.ShiftChanged)
        }
    }

    private fun dismissAlternates() {
        popup.dismiss()
        alternatesPointerId = -1
        alternatesItems = emptyList()
        invalidate()
    }

    // ---- delete auto-repeat -----------------------------------------------

    private val deleteRunnable = object : Runnable {
        override fun run() {
            listener?.onAction(KeyAction.Delete)
            listener?.onKeyDownFeedback(KeyType.DELETE)
            handler.postDelayed(this, REPEAT_INTERVAL)
        }
    }

    // ---- glide gesture ----------------------------------------------------

    private fun beginGesture(id: Int) {
        gesturePointerId = id
        gestureMaybe = false
        cancelLongPress(id)
        popup.dismiss()
        previewPointerId = -1
        pressedKeys.clear()
        gesturePoints.clear()
        gesturePoints.add(PointF(gestureStart.x, gestureStart.y))
        invalidate()
    }

    private fun endGesture() {
        if (gesturePoints.size >= 2) {
            listener?.onAction(KeyAction.GesturePath(ArrayList(gesturePoints)))
        }
        gesturePoints.clear()
        gesturePointerId = -1
        invalidate()
    }

    // ---- misc -------------------------------------------------------------

    private fun resetTransientState() {
        handler.removeCallbacksAndMessages(null)
        pointerKeys.clear()
        pressedKeys.clear()
        deletePointerId = -1
        alternatesPointerId = -1
        gesturePointerId = -1
        gestureCandidateId = -1
        gestureMaybe = false
        gesturePoints.clear()
        popup.dismiss()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        resetTransientState()
    }

    /** Linear blend of two ARGB colours (t in 0..1). Used to darken the pressed action key. */
    private fun blend(a: Int, b: Int, t: Float): Int {
        val ia = 1f - t
        return Color.argb(
            (Color.alpha(a) * ia + Color.alpha(b) * t).toInt(),
            (Color.red(a) * ia + Color.red(b) * t).toInt(),
            (Color.green(a) * ia + Color.green(b) * t).toInt(),
            (Color.blue(a) * ia + Color.blue(b) * t).toInt()
        )
    }

    companion object {
        private const val LONG_PRESS_MS = 320L
        private const val DOUBLE_TAP_MS = 300L
        private const val REPEAT_DELAY = 400L   // before delete starts repeating
        private const val REPEAT_INTERVAL = 55L // between repeats
    }
}
