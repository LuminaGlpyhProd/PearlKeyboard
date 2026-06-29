package com.pearl.keyboard.feature.emoji

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.emoji2.widget.EmojiTextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pearl.keyboard.ime.PanelActions
import com.pearl.keyboard.theme.KeyboardTheme
import com.pearl.keyboard.util.dpInt

/**
 * iOS/Gboard-style emoji picker: a scrollable grid of the selected category with a
 * bottom bar of category tabs plus ABC (close) and backspace. Recently used emoji are
 * remembered (the leftmost "🕘" tab).
 */
@SuppressLint("ViewConstructor")
class EmojiPanelView(context: Context) : LinearLayout(context) {

    var actions: PanelActions? = null

    private val grid = RecyclerView(context)
    private val adapter = EmojiAdapter()
    private val tabBar = LinearLayout(context)
    private val tabViews = ArrayList<TextView>()
    private var theme: KeyboardTheme = KeyboardTheme.light(false)
    private var selected = 1
    private var cellPx = context.dpInt(54f)   // larger, less cramped touch targets

    init {
        EmojiData.init(context)
        orientation = VERTICAL

        grid.layoutManager = GridLayoutManager(context, 8)
        grid.adapter = adapter
        grid.clipToPadding = false
        grid.setPadding(context.dpInt(8f), context.dpInt(10f), context.dpInt(8f), context.dpInt(10f))
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        buildBottomBar(context)
        selected = if (EmojiData.recents().isEmpty()) 1 else 0
        showCategory(selected)
    }

    private fun buildBottomBar(context: Context) {
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val barHeight = context.dpInt(46f)

        bar.addView(makeBarButton("ABC") { actions?.closePanel() }, barButtonParams())

        val scroller = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        tabBar.orientation = HORIZONTAL
        EmojiData.categories.forEachIndexed { index, cat ->
            val tab = makeBarButton(cat.tab) { showCategory(index) }
            tabViews.add(tab)
            tabBar.addView(tab, LinearLayout.LayoutParams(context.dpInt(44f), barHeight))
        }
        scroller.addView(tabBar)
        bar.addView(scroller, LinearLayout.LayoutParams(0, barHeight, 1f))

        bar.addView(makeBarButton("⌫") { actions?.backspace() }, barButtonParams())

        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, barHeight))
    }

    private fun barButtonParams() =
        LinearLayout.LayoutParams(context.dpInt(52f), context.dpInt(46f))

    private fun makeBarButton(text: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 16f
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun showCategory(index: Int) {
        selected = index
        val items = if (index == 0) EmojiData.recents() else EmojiData.categories[index].emojis
        adapter.submit(items)
        tabViews.forEachIndexed { i, tv ->
            tv.setTextColor(if (i == index) theme.accent else theme.suggestionText)
        }
    }

    fun setTheme(t: KeyboardTheme) {
        theme = t
        setBackgroundColor(t.background)
        tabViews.forEachIndexed { i, tv ->
            tv.setTextColor(if (i == selected) t.accent else t.suggestionText)
        }
        adapter.textColor = t.keyText
        adapter.notifyDataSetChanged()
        // ABC + backspace buttons (first and last children of the bottom bar).
        (getChildAt(1) as? LinearLayout)?.let { bar ->
            (bar.getChildAt(0) as? TextView)?.setTextColor(t.accent)
            (bar.getChildAt(2) as? TextView)?.setTextColor(t.keyText)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            val span = (w / cellPx).coerceAtLeast(6)
            (grid.layoutManager as GridLayoutManager).spanCount = span
        }
    }

    // ---- adapter ----------------------------------------------------------

    private inner class EmojiAdapter : RecyclerView.Adapter<EmojiAdapter.VH>() {
        private val data = ArrayList<String>()
        var textColor = 0

        @SuppressLint("NotifyDataSetChanged")
        fun submit(items: List<String>) {
            data.clear()
            data.addAll(items)
            notifyDataSetChanged()
        }

        inner class VH(val tv: EmojiTextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = EmojiTextView(parent.context).apply {
                gravity = Gravity.CENTER
                textSize = 26f
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellPx)
                isClickable = true
            }
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val emoji = data[position]
            holder.tv.text = emoji
            if (textColor != 0) holder.tv.setTextColor(textColor)
            holder.tv.setOnClickListener {
                actions?.commitText(emoji)
                EmojiData.addRecent(emoji)
            }
        }

        override fun getItemCount() = data.size
    }
}
