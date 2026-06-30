package com.pearl.keyboard.feature.gif

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pearl.keyboard.ime.PanelActions
import com.pearl.keyboard.theme.KeyboardTheme
import com.pearl.keyboard.util.dpInt
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * GIF panel (#6). Powered by Tenor when [TENOR_API_KEY] is set; otherwise shows setup
 * instructions (default build = no key = no network).
 *
 * Uses tappable category chips rather than a text field, because an EditText embedded
 * in an IME can't receive keystrokes (the IME *is* the keyboard). Trending shows on
 * open; tapping a GIF inserts it via the rich-content API with a URL fallback.
 */
@SuppressLint("ViewConstructor")
class GifPanelView(context: Context) : LinearLayout(context) {

    var actions: PanelActions? = null
    private var theme: KeyboardTheme = KeyboardTheme.light(false)

    private val tenor: TenorClient? = if (TENOR_API_KEY.isNotEmpty()) TenorClient(TENOR_API_KEY) else null
    private val results = ArrayList<TenorClient.Gif>()
    private val adapter = GifAdapter()
    private val grid = RecyclerView(context)
    private val close = TextView(context)
    private val message = TextView(context)
    private val chipViews = ArrayList<TextView>()

    private val categories = listOf(
        "Trending" to "", "😂" to "funny", "❤️" to "love", "👍" to "thumbs up",
        "🎉" to "celebrate", "😮" to "wow", "😢" to "sad", "🔥" to "fire",
        "🐱" to "cat", "🐶" to "dog", "👋" to "hello", "👏" to "applause"
    )

    init {
        orientation = VERTICAL

        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dpInt(12f), context.dpInt(4f), context.dpInt(12f), context.dpInt(4f))
        }
        close.text = "ABC"
        close.textSize = 15f
        close.setPadding(0, 0, context.dpInt(12f), 0)
        close.setOnClickListener { actions?.closePanel() }
        bar.addView(close)

        if (tenor == null) {
            bar.addView(TextView(context).apply { text = "GIF"; textSize = 16f })
            addView(bar)
            message.text = "GIF search isn't configured.\n\nAdd a Tenor API key to " +
                "GifPanelView.TENOR_API_KEY and rebuild. Tap ABC to go back."
            message.gravity = Gravity.CENTER
            message.setPadding(context.dpInt(24f), context.dpInt(24f), context.dpInt(24f), 0)
            addView(message, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            // Category chips
            val scroller = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
            val chipRow = LinearLayout(context).apply { orientation = HORIZONTAL }
            categories.forEachIndexed { index, (label, term) ->
                val chip = TextView(context).apply {
                    text = label
                    textSize = 15f
                    setPadding(context.dpInt(12f), context.dpInt(8f), context.dpInt(12f), context.dpInt(8f))
                    isClickable = true
                    setOnClickListener { selectCategory(index, term) }
                }
                chipViews.add(chip)
                chipRow.addView(chip)
            }
            scroller.addView(chipRow)
            bar.addView(scroller, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(bar)

            grid.layoutManager = GridLayoutManager(context, 2)
            grid.adapter = adapter
            grid.clipToPadding = false
            grid.setPadding(context.dpInt(6f), context.dpInt(6f), context.dpInt(6f), context.dpInt(6f))
            addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

            selectCategory(0, "")
        }
    }

    fun setTheme(t: KeyboardTheme) {
        theme = t
        setBackgroundColor(t.background)
        close.setTextColor(t.accent)
        message.setTextColor(t.suggestionText)
        chipViews.forEach { it.setTextColor(t.keyText) }
    }

    private fun selectCategory(index: Int, term: String) {
        chipViews.forEachIndexed { i, tv -> tv.setTextColor(if (i == index) theme.accent else theme.keyText) }
        loadGifs(term)
    }

    private fun loadGifs(term: String) {
        val t = tenor ?: return
        Thread {
            val list = runCatching { if (term.isEmpty()) t.trending() else t.search(term) }
                .getOrDefault(emptyList())
            grid.post {
                results.clear()
                results.addAll(list)
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    private fun insertGif(gif: TenorClient.Gif) {
        Thread {
            val file = runCatching { downloadTo(gif.fullUrl) }.getOrNull()
            grid.post {
                if (file == null) {
                    actions?.commitText(gif.fullUrl)
                } else {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    actions?.commitMedia(uri, "image/gif", gif.fullUrl)
                }
            }
        }.start()
    }

    private fun downloadTo(url: String): File {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 20000
            instanceFollowRedirects = true
        }
        try {
            val file = File(context.cacheDir, "gif_share.gif")
            conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
            return file
        } finally {
            conn.disconnect()
        }
    }

    private inner class GifAdapter : RecyclerView.Adapter<GifAdapter.VH>() {
        inner class VH(val iv: ImageView) : RecyclerView.ViewHolder(iv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val iv = ImageView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dpInt(110f)).apply {
                    val m = context.dpInt(3f)
                    setMargins(m, m, m, m)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                isClickable = true
            }
            return VH(iv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val gif = results[position]
            GifImageLoader.load(gif.previewUrl, holder.iv)
            holder.iv.setOnClickListener { insertGif(gif) }
        }

        override fun getItemCount() = results.size
    }

    companion object {
        /** Put your Tenor v2 API key here to enable GIF search. */
        const val TENOR_API_KEY = ""
    }
}
