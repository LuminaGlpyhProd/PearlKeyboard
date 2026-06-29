package com.pearl.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import com.pearl.keyboard.theme.KeyboardTheme
import com.pearl.keyboard.util.dp

/**
 * The rising "key cap" bubble shown above the finger while typing (the signature
 * iOS effect), and the wider alternates bar shown on long-press (é è ê …).
 *
 * Implemented as a non-touchable [PopupWindow] with clipping disabled so the bubble
 * can extend above the top edge of the keyboard, exactly like iOS. The owning
 * KeyboardView computes all geometry in *window* coordinates and passes it in here;
 * this class is purely presentation.
 */
class KeyPreviewPopup(context: Context) {

    private val content = PreviewContent(context)
    private val window = PopupWindow(content, 0, 0, /* focusable = */ false).apply {
        isClippingEnabled = false
        isTouchable = false
        isFocusable = false
        setBackgroundDrawable(null)
        animationStyle = 0
    }

    fun setTheme(theme: KeyboardTheme) {
        content.theme = theme
    }

    /** Single-character preview bubble. Geometry is in window coordinates. */
    fun showChar(anchor: View, xWin: Int, yWin: Int, w: Int, h: Int, bodyH: Float, neckHalf: Float, text: String) {
        content.configure(
            alternates = false, text = text, items = emptyList(), selected = -1,
            wPx = w, hPx = h, bodyHeight = bodyH, neckHalfWidth = neckHalf, cellWidth = 0f
        )
        place(anchor, xWin, yWin, w, h)
    }

    /** Long-press alternates bar. */
    fun showAlternates(
        anchor: View, xWin: Int, yWin: Int, w: Int, h: Int,
        bodyH: Float, items: List<String>, selected: Int, cellW: Float
    ) {
        content.configure(
            alternates = true, text = "", items = items, selected = selected,
            wPx = w, hPx = h, bodyHeight = bodyH, neckHalfWidth = 0f, cellWidth = cellW
        )
        place(anchor, xWin, yWin, w, h)
    }

    fun setSelected(index: Int) {
        if (content.selected != index) {
            content.selected = index
            content.invalidate()
        }
    }

    val selectedItem: String?
        get() = content.items.getOrNull(content.selected)

    private fun place(anchor: View, x: Int, y: Int, w: Int, h: Int) {
        window.width = w
        window.height = h
        try {
            if (window.isShowing) {
                window.update(x, y, w, h)
            } else if (anchor.windowToken != null) {
                window.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
            }
        } catch (_: Exception) {
            // Window token not ready / IME tearing down — preview is non-essential.
        }
        content.invalidate()
    }

    fun dismiss() {
        if (window.isShowing) runCatching { window.dismiss() }
    }

    // ---------------------------------------------------------------------

    @SuppressLint("ViewConstructor")
    private class PreviewContent(context: Context) : View(context) {
        var theme: KeyboardTheme? = null

        private var alternates = false
        var items: List<String> = emptyList(); private set
        var selected = -1
        private var text = ""
        private var wPx = 0
        private var hPx = 0
        private var bodyHeight = 0f
        private var neckHalfWidth = 0f
        private var cellWidth = 0f

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            // Soft shadow under the bubble needs a software layer for arbitrary shapes.
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        fun configure(
            alternates: Boolean, text: String, items: List<String>, selected: Int,
            wPx: Int, hPx: Int, bodyHeight: Float, neckHalfWidth: Float, cellWidth: Float
        ) {
            this.alternates = alternates
            this.text = text
            this.items = items
            this.selected = selected
            this.wPx = wPx
            this.hPx = hPx
            this.bodyHeight = bodyHeight
            this.neckHalfWidth = neckHalfWidth
            this.cellWidth = cellWidth
            requestLayout()
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(wPx, hPx)
        }

        override fun onDraw(canvas: Canvas) {
            val t = theme ?: return
            val w = wPx.toFloat()
            val r = context.dp(7f)

            bgPaint.color = t.popupBg
            bgPaint.setShadowLayer(context.dp(7f), 0f, context.dp(2.5f), t.keyShadow)

            // Body (the wide rounded cap).
            canvas.drawRoundRect(0f, 0f, w, bodyHeight, r, r, bgPaint)

            if (!alternates && neckHalfWidth > 0f) {
                // Neck: a narrower rounded rect that overlaps the body and reaches the key.
                val cx = w / 2f
                canvas.drawRoundRect(
                    cx - neckHalfWidth, bodyHeight - r * 2, cx + neckHalfWidth, hPx.toFloat(),
                    r, r, bgPaint
                )
            }
            bgPaint.clearShadowLayer()

            if (alternates) {
                textPaint.textSize = bodyHeight * 0.42f
                val fm = textPaint.fontMetrics
                val baseline = bodyHeight / 2f - (fm.ascent + fm.descent) / 2f
                for (i in items.indices) {
                    val cl = i * cellWidth
                    if (i == selected) {
                        selPaint.color = t.accent
                        val pad = context.dp(4f)
                        canvas.drawRoundRect(cl + pad, pad, cl + cellWidth - pad, bodyHeight - pad, r, r, selPaint)
                        textPaint.color = t.accentText
                    } else {
                        textPaint.color = t.popupText
                    }
                    canvas.drawText(items[i], cl + cellWidth / 2f, baseline, textPaint)
                }
            } else {
                textPaint.color = t.popupText
                textPaint.textSize = bodyHeight * 0.5f
                val fm = textPaint.fontMetrics
                val baseline = bodyHeight / 2f - (fm.ascent + fm.descent) / 2f
                canvas.drawText(text, w / 2f, baseline, textPaint)
            }
        }
    }
}
