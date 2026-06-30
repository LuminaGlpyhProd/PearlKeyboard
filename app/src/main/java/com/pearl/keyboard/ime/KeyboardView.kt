package com.pearl.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
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
import kotlin.math.sqrt

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
    var longPressDelayMs = 320L
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
    // Proximity radius² for touch correction: taps in gaps / just off a key still register.
    private val proximityLimitSq = context.dp(36f).let { it * it }
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

    // Optional custom background image (#12). Null => solid theme colour.
    private var bgBitmap: Bitmap? = null
    private var bgDimAlpha = 0
    private val bgSrc = Rect()
    private val bgDst = Rect()
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glyphPath = Path()   // reused for shift/delete glyphs (no per-frame alloc)

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
    private var deleteActive = false
    private var alternatesPointerId = -1

    private var gesturePointerId = -1
    private var gestureCandidateId = -1
    private var gestureMaybe = false
    private val gestureStart = PointF()
    private val gesturePoints = ArrayList<PointF>()
    private var gestureStartTime = 0L

    // Swipe-activation gating (configurable) so rapid taps never trigger glide typing (#1).
    var gestureMinDistancePx = context.dp(26f)   // must travel at least this far to start a swipe
    var gestureMinSpeedPxPerMs = 0.32f           // …and be moving at least this fast
    var gestureMaxStartDelayMs = 280L            // …within this long of touch-down

    private var lastShiftTap = 0L
    private var lastGesturePreview = 0L

    // gesture trail fade-out
    private var trailFadeAlpha = 0f
    private val fadeTrailPoints = ArrayList<PointF>()

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

    /** Set (or clear) the custom background image (#12). [dimPercent] darkens it 0..100. */
    fun setBackgroundImage(bitmap: Bitmap?, dimPercent: Int) {
        bgBitmap = bitmap
        bgDimAlpha = (dimPercent * 255 / 100).coerceIn(0, 255)
        invalidate()
    }

    private fun drawBackground(canvas: Canvas) {
        val bmp = bgBitmap
        if (bmp == null || bmp.isRecycled || width == 0 || height == 0) {
            canvas.drawColor(theme.background)
            return
        }
        // Centre-crop the bitmap to fill the keyboard.
        val scale = max(width / bmp.width.toFloat(), height / bmp.height.toFloat())
        val cw = width / scale
        val ch = height / scale
        val left = (bmp.width - cw) / 2f
        val top = (bmp.height - ch) / 2f
        bgSrc.set(left.toInt(), top.toInt(), (left + cw).toInt(), (top + ch).toInt())
        bgDst.set(0, 0, width, height)
        canvas.drawBitmap(bmp, bgSrc, bgDst, bgPaint)
        if (bgDimAlpha > 0) canvas.drawColor(bgDimAlpha shl 24)  // black overlay
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

    /**
     * Resolve a touch to a key. A direct hit wins immediately; otherwise the touch is
     * snapped to the NEAREST key (by distance to its rectangle). This widens each
     * key's *effective* hit area into the gaps and just past the edges without changing
     * its drawn size — the core of Gboard-style fast-typing accuracy. Stray touches
     * beyond [proximityLimitSq] are rejected so nothing far away gets grabbed.
     */
    private fun keyAt(x: Float, y: Float): PositionedKey? {
        var nearest: PositionedKey? = null
        var nearestDistSq = Float.MAX_VALUE
        for (pk in positioned) {
            if (pk.key.isSpacer) continue
            val r = pk.rect
            if (r.contains(x, y)) return pk
            val dx = if (x < r.left) r.left - x else if (x > r.right) x - r.right else 0f
            val dy = if (y < r.top) r.top - y else if (y > r.bottom) y - r.bottom else 0f
            val dSq = dx * dx + dy * dy
            if (dSq < nearestDistSq) {
                nearestDistSq = dSq
                nearest = pk
            }
        }
        return if (nearestDistSq <= proximityLimitSq) nearest else null
    }

    /** Strict hit-test: only a key whose rect actually contains the point (no proximity). */
    private fun directKeyAt(x: Float, y: Float): PositionedKey? =
        positioned.firstOrNull { !it.key.isSpacer && it.rect.contains(x, y) }

    // ======================================================================
    // Drawing
    // ======================================================================

    override fun onDraw(canvas: Canvas) {
        drawBackground(canvas)
        for (pk in positioned) {
            if (pk.key.isSpacer) continue
            drawKey(canvas, pk)
        }
        if (gesturePointerId != -1 && gesturePoints.size > 1) {
            drawTrail(canvas, gesturePoints, 1f)
        } else if (trailFadeAlpha > 0f && fadeTrailPoints.size > 1) {
            drawTrail(canvas, fadeTrailPoints, trailFadeAlpha)
        }
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

    /**
     * Draw the glide trail as alpha- and width-tapered round segments: the head (most
     * recent points) is brightest/thickest, fading toward the tail. [globalAlpha] (0..1)
     * scales the whole thing for the post-release fade-out.
     */
    private fun drawTrail(canvas: Canvas, pts: List<PointF>, globalAlpha: Float) {
        if (pts.size < 2) return
        val base = theme.gestureTrail
        val baseA = Color.alpha(base)
        val r = Color.red(base); val g = Color.green(base); val b = Color.blue(base)
        val n = pts.size
        val wMin = context.dp(5f)
        val wMax = context.dp(12f)
        for (i in 1 until n) {
            val t = i.toFloat() / (n - 1)                  // 0 = tail, 1 = head
            val a = (baseA * globalAlpha * (0.20f + 0.80f * t)).toInt().coerceIn(0, 255)
            trailPaint.color = Color.argb(a, r, g, b)
            trailPaint.strokeWidth = wMin + (wMax - wMin) * t
            canvas.drawLine(pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y, trailPaint)
        }
    }

    // ---- vector glyphs for special keys -----------------------------------

    private fun drawShift(canvas: Canvas, rect: RectF, color: Int) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val s = min(rect.width(), rect.height()) * 0.30f
        iconPaint.color = color
        iconPaint.style = Paint.Style.FILL
        glyphPath.reset()
        glyphPath.apply {
            moveTo(cx, cy - s)               // arrow tip
            lineTo(cx - s, cy)               // left base
            lineTo(cx - s * 0.45f, cy)
            lineTo(cx - s * 0.45f, cy + s * 0.75f) // stem
            lineTo(cx + s * 0.45f, cy + s * 0.75f)
            lineTo(cx + s * 0.45f, cy)
            lineTo(cx + s, cy)               // right base
            close()
        }
        canvas.drawPath(glyphPath, iconPaint)
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
        glyphPath.reset()
        glyphPath.apply {
            moveTo(cx - w, cy)
            lineTo(cx - w * 0.45f, cy - h)
            lineTo(cx + w, cy - h)
            lineTo(cx + w, cy + h)
            lineTo(cx - w * 0.45f, cy + h)
            close()
        }
        canvas.drawPath(glyphPath, iconPaint)
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
        // Delete feedback is played by the service only when something is actually
        // deleted (see onDelete), so it stops the instant the field is empty.
        if (pk.key.type != KeyType.DELETE) listener?.onKeyDownFeedback(pk.key.type)

        when (pk.key.type) {
            KeyType.DELETE -> {
                deletePointerId = id
                deleteActive = true
                listener?.onAction(KeyAction.Delete)
                handler.postDelayed(deleteRunnable, REPEAT_DELAY)
            }
            KeyType.CHAR -> {
                if (showPreview()) showCharPreview(id, pk)
                if (pk.key.popup.isNotEmpty()) scheduleLongPress(id, pk)
                if (gestureEnabled && pointerKeys.size() == 1 && layout.id == LayoutId.LETTERS) {
                    gestureCandidateId = id
                    gestureStart.set(x, y)
                    gestureStartTime = SystemClock.uptimeMillis()
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
            // Throttled live prediction while swiping (Gboard-style).
            val now = SystemClock.uptimeMillis()
            if (now - lastGesturePreview >= GESTURE_PREVIEW_MS) {
                lastGesturePreview = now
                listener?.onGesturePreview(ArrayList(gesturePoints))
            }
            invalidate()
            return
        }
        if (id == alternatesPointerId) {
            updateAlternateSelection(x)
            return
        }
        val pk = pointerKeys.get(id) ?: return

        // Promote to a glide gesture ONLY for a deliberate move: far enough, fast enough,
        // and soon enough after touch-down — so rapid tap-typing is never mistaken for a
        // swipe (#1).
        if (gestureMaybe && id == gestureCandidateId && layout.id == LayoutId.LETTERS) {
            val dx = x - gestureStart.x
            val dy = y - gestureStart.y
            val distSq = dx * dx + dy * dy
            if (distSq > gestureMinDistancePx * gestureMinDistancePx) {
                val elapsed = (SystemClock.uptimeMillis() - gestureStartTime).coerceAtLeast(1L)
                val speed = sqrt(distSq.toDouble()) / elapsed
                if (elapsed <= gestureMaxStartDelayMs && speed >= gestureMinSpeedPxPerMs) {
                    beginGesture(id)
                    return
                }
                gestureMaybe = false // moved far but too slow/late — treat as a tap, not a swipe
            }
        }

        // Slide to an adjacent letter to correct aim. Use a DIRECT hit (finger firmly over
        // the other key), not proximity, so a fast tap that drifts toward a gap doesn't
        // commit a neighbouring key (#5).
        if (pk.key.type == KeyType.CHAR) {
            val nk = directKeyAt(x, y)
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
            deleteActive = false
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
        deleteActive = false
        alternatesPointerId = -1
        previewPointerId = -1
        gesturePointerId = -1
        gestureCandidateId = -1
        gestureMaybe = false
        gesturePoints.clear()
        trailFadeAlpha = 0f
        fadeTrailPoints.clear()
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
        handler.postDelayed(longPressRunnable, longPressDelayMs)
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
            if (!deleteActive) return
            // The service may call cancelDeleteRepeat() from inside onAction (when the
            // field is now empty), which flips deleteActive off — so we must re-check it
            // before re-scheduling, otherwise the repeat would run away forever.
            listener?.onAction(KeyAction.Delete)
            if (deleteActive) handler.postDelayed(this, REPEAT_INTERVAL)
        }
    }

    /**
     * Stop the backspace auto-repeat immediately. Called by the service when nothing is
     * left to delete, and on key release. Clears [deleteActive] FIRST so an in-flight
     * [deleteRunnable] cannot re-schedule itself. The pointer id is left intact so the
     * eventual ACTION_UP still cleans up normally.
     */
    fun cancelDeleteRepeat() {
        deleteActive = false
        handler.removeCallbacks(deleteRunnable)
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
        val pts = ArrayList(gesturePoints)
        if (pts.size >= 2) listener?.onAction(KeyAction.GesturePath(pts))
        gesturePoints.clear()
        gesturePointerId = -1
        if (pts.size >= 2) startTrailFade(pts)
        invalidate()
    }

    private fun startTrailFade(pts: List<PointF>) {
        fadeTrailPoints.clear()
        fadeTrailPoints.addAll(pts)
        trailFadeAlpha = 1f
        handler.removeCallbacks(trailFadeRunnable)
        handler.post(trailFadeRunnable)
    }

    private val trailFadeRunnable = object : Runnable {
        override fun run() {
            trailFadeAlpha -= 0.14f
            if (trailFadeAlpha <= 0f) {
                trailFadeAlpha = 0f
                fadeTrailPoints.clear()
            } else {
                handler.postDelayed(this, 16L)
            }
            invalidate()
        }
    }

    // ---- misc -------------------------------------------------------------

    private fun resetTransientState() {
        handler.removeCallbacksAndMessages(null)
        pointerKeys.clear()
        pressedKeys.clear()
        deletePointerId = -1
        deleteActive = false
        alternatesPointerId = -1
        gesturePointerId = -1
        gestureCandidateId = -1
        gestureMaybe = false
        gesturePoints.clear()
        trailFadeAlpha = 0f
        fadeTrailPoints.clear()
        handler.removeCallbacks(trailFadeRunnable)
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
        private const val DOUBLE_TAP_MS = 300L
        private const val REPEAT_DELAY = 400L      // before delete starts repeating
        private const val REPEAT_INTERVAL = 55L    // between repeats
        private const val GESTURE_PREVIEW_MS = 90L // throttle for live swipe prediction
    }
}
