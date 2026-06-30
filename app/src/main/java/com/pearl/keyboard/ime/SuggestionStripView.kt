package com.pearl.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.pearl.keyboard.R
import com.pearl.keyboard.theme.KeyboardTheme
import com.pearl.keyboard.util.dp

/**
 * The bar above the keys. When the user is typing it shows up to three candidate
 * words (iOS-style predictive bar, with the autocorrect pick subtly highlighted).
 * When idle it shows a small Gboard-like toolbar (emoji / GIF / clipboard / voice /
 * settings) so those features are reachable.
 */
class SuggestionStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Toolbar { EMOJI, GIF, CLIPBOARD, VOICE, SETTINGS }

    interface Listener {
        fun onSuggestionPicked(text: String, index: Int)
        fun onToolbarAction(action: Toolbar)
    }

    var listener: Listener? = null

    private var theme: KeyboardTheme = KeyboardTheme.light(false)
    private var suggestions: List<String> = emptyList()
    private var highlightIndex = -1
    // Emoji intentionally omitted here — the bottom row already has an emoji key (#8).
    private val toolbar = listOf(Toolbar.GIF, Toolbar.CLIPBOARD, Toolbar.VOICE, Toolbar.SETTINGS)

    private val stripHeight = context.resources.getDimension(R.dimen.suggestion_strip_height)
    private val textSize = context.resources.getDimension(R.dimen.suggestion_text_size)

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setTheme(t: KeyboardTheme) {
        theme = t
        invalidate()
    }

    fun setSuggestions(items: List<String>, highlight: Int) {
        suggestions = items
        highlightIndex = highlight
        invalidate()
    }

    /** No suggestions → fall back to the idle toolbar. */
    fun clear() {
        suggestions = emptyList()
        highlightIndex = -1
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), stripHeight.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        val w = width.toFloat()
        val h = height.toFloat()

        // Hairline under the strip.
        dividerPaint.color = theme.suggestionDivider
        canvas.drawRect(0f, h - context.dp(0.5f), w, h, dividerPaint)

        if (suggestions.isEmpty()) {
            drawToolbar(canvas, w, h)
            return
        }

        val n = minOf(3, suggestions.size)
        val slot = w / n
        textPaint.color = theme.suggestionText
        textPaint.textSize = textSize
        val fm = textPaint.fontMetrics
        val baseline = h / 2f - (fm.ascent + fm.descent) / 2f

        for (i in 0 until n) {
            val cx = slot * i + slot / 2f
            if (i == highlightIndex) {
                pillPaint.color = theme.functionKeyBg
                val pad = context.dp(6f)
                val pill = RectF(slot * i + pad, h * 0.18f, slot * (i + 1) - pad, h * 0.82f)
                canvas.drawRoundRect(pill, h * 0.32f, h * 0.32f, pillPaint)
            } else if (i > 0) {
                dividerPaint.color = theme.suggestionDivider
                canvas.drawRect(slot * i, h * 0.25f, slot * i + context.dp(0.5f), h * 0.75f, dividerPaint)
            }
            val shown = TextUtils.ellipsize(suggestions[i], textPaint, slot * 0.86f, TextUtils.TruncateAt.END)
            canvas.drawText(shown, 0, shown.length, cx, baseline, textPaint)
        }
    }

    private fun drawToolbar(canvas: Canvas, w: Float, h: Float) {
        val n = toolbar.size
        val slot = w / n
        iconPaint.color = theme.suggestionText
        for (i in toolbar.indices) {
            val cx = slot * i + slot / 2f
            val cy = h / 2f
            when (toolbar[i]) {
                Toolbar.EMOJI -> drawEmoji(canvas, cx, cy)
                Toolbar.GIF -> drawGif(canvas, cx, cy)
                Toolbar.CLIPBOARD -> drawClipboard(canvas, cx, cy)
                Toolbar.VOICE -> drawMic(canvas, cx, cy)
                Toolbar.SETTINGS -> drawGear(canvas, cx, cy)
            }
        }
    }

    private fun drawEmoji(canvas: Canvas, cx: Float, cy: Float) {
        val r = context.dp(10f)
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = context.dp(1.6f)
        canvas.drawCircle(cx, cy, r, iconPaint)
        iconPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx - r * 0.38f, cy - r * 0.25f, context.dp(1.5f), iconPaint)
        canvas.drawCircle(cx + r * 0.38f, cy - r * 0.25f, context.dp(1.5f), iconPaint)
        iconPaint.style = Paint.Style.STROKE
        canvas.drawArc(RectF(cx - r * 0.5f, cy - r * 0.1f, cx + r * 0.5f, cy + r * 0.55f), 20f, 140f, false, iconPaint)
    }

    private fun drawGif(canvas: Canvas, cx: Float, cy: Float) {
        textPaint.color = theme.suggestionText
        textPaint.textSize = context.dp(13f)
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        val fm = textPaint.fontMetrics
        canvas.drawText("GIF", cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private fun drawClipboard(canvas: Canvas, cx: Float, cy: Float) {
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = context.dp(1.6f)
        val w = context.dp(13f)
        val hh = context.dp(15f)
        canvas.drawRoundRect(cx - w / 2, cy - hh / 2, cx + w / 2, cy + hh / 2, context.dp(2.5f), context.dp(2.5f), iconPaint)
        canvas.drawRoundRect(cx - w * 0.22f, cy - hh / 2 - context.dp(2f), cx + w * 0.22f, cy - hh / 2 + context.dp(3f), context.dp(1.5f), context.dp(1.5f), iconPaint)
    }

    private fun drawMic(canvas: Canvas, cx: Float, cy: Float) {
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = context.dp(1.6f)
        val capW = context.dp(7f)
        val capTop = cy - context.dp(9f)
        val capBot = cy + context.dp(2f)
        canvas.drawRoundRect(cx - capW / 2, capTop, cx + capW / 2, capBot, capW / 2, capW / 2, iconPaint)
        canvas.drawArc(RectF(cx - capW, capBot - capW, cx + capW, capBot + capW), 0f, 180f, false, iconPaint)
        canvas.drawLine(cx, capBot + capW, cx, cy + context.dp(11f), iconPaint)
    }

    private fun drawGear(canvas: Canvas, cx: Float, cy: Float) {
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = context.dp(1.6f)
        val r = context.dp(8f)
        canvas.drawCircle(cx, cy, r, iconPaint)
        canvas.drawCircle(cx, cy, context.dp(2.5f), iconPaint)
        for (k in 0 until 8) {
            val a = Math.toRadians((k * 45).toDouble())
            val sx = (cx + Math.cos(a) * r).toFloat()
            val sy = (cy + Math.sin(a) * r).toFloat()
            val ex = (cx + Math.cos(a) * (r + context.dp(3f))).toFloat()
            val ey = (cy + Math.sin(a) * (r + context.dp(3f))).toFloat()
            canvas.drawLine(sx, sy, ex, ey, iconPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val count = if (suggestions.isEmpty()) toolbar.size else minOf(3, suggestions.size)
            if (count == 0) return true
            val index = (event.x / (width.toFloat() / count)).toInt().coerceIn(0, count - 1)
            if (suggestions.isEmpty()) {
                listener?.onToolbarAction(toolbar[index])
            } else {
                listener?.onSuggestionPicked(suggestions[index], index)
            }
        }
        return true
    }
}
