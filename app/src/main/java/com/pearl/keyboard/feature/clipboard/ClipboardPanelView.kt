package com.pearl.keyboard.feature.clipboard

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pearl.keyboard.ime.PanelActions
import com.pearl.keyboard.theme.KeyboardTheme
import com.pearl.keyboard.util.dpInt

/**
 * Clipboard history: tap an entry to paste it, long-press to delete it. "Clear"
 * empties the list. Entries are captured by the IME's clipboard watcher.
 */
@SuppressLint("ViewConstructor")
class ClipboardPanelView(context: Context) : LinearLayout(context) {

    var actions: PanelActions? = null

    private val list = RecyclerView(context)
    private val adapter = ClipAdapter()
    private val empty = TextView(context)
    private val close = TextView(context)
    private val title = TextView(context)
    private val clear = TextView(context)
    private var theme: KeyboardTheme = KeyboardTheme.light(false)

    init {
        orientation = VERTICAL

        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dpInt(12f), context.dpInt(6f), context.dpInt(12f), context.dpInt(6f))
        }
        close.text = "ABC"
        close.textSize = 15f
        close.setOnClickListener { actions?.closePanel() }
        title.text = "Clipboard"
        title.textSize = 16f
        title.setPadding(context.dpInt(16f), 0, 0, 0)
        clear.text = "Clear"
        clear.textSize = 15f
        clear.setOnClickListener {
            ClipboardHistory.clear()
            refresh()
        }
        bar.addView(close)
        bar.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(clear)
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        empty.text = "Copied text shows up here"
        empty.gravity = Gravity.CENTER
        empty.visibility = GONE
        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refresh() {
        val items = ClipboardHistory.all()
        adapter.submit(items)
        empty.visibility = if (items.isEmpty()) VISIBLE else GONE
        list.visibility = if (items.isEmpty()) GONE else VISIBLE
    }

    fun setTheme(t: KeyboardTheme) {
        theme = t
        setBackgroundColor(t.background)
        close.setTextColor(t.accent)
        clear.setTextColor(t.accent)
        title.setTextColor(t.keyText)
        empty.setTextColor(t.suggestionText)
        adapter.textColor = t.keyText
        adapter.dividerColor = t.suggestionDivider
        adapter.notifyDataSetChanged()
    }

    private inner class ClipAdapter : RecyclerView.Adapter<ClipAdapter.VH>() {
        private val data = ArrayList<String>()
        var textColor = 0
        var dividerColor = 0

        @SuppressLint("NotifyDataSetChanged")
        fun submit(items: List<String>) {
            data.clear()
            data.addAll(items)
            notifyDataSetChanged()
        }

        inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(context.dpInt(16f), context.dpInt(14f), context.dpInt(16f), context.dpInt(14f))
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                textSize = 15f
                isClickable = true
            }
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val text = data[position]
            holder.tv.text = text
            if (textColor != 0) holder.tv.setTextColor(textColor)
            holder.tv.setOnClickListener { actions?.commitText(text) }
            holder.tv.setOnLongClickListener {
                ClipboardHistory.remove(text)
                refresh()
                true
            }
        }

        override fun getItemCount() = data.size
    }
}
