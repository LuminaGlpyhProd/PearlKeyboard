package com.pearl.keyboard.feature.gif

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * Tiny async image loader for the GIF grid (#6). Downloads GIF bytes (cached), then
 * decodes them with [ImageDecoder] so the previews actually animate (API 28+, and our
 * minSdk is 29). No third-party image library needed.
 */
object GifImageLoader {

    private val cache = object : LruCache<String, ByteArray>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }
    private val executor = Executors.newFixedThreadPool(3)

    fun load(url: String, into: ImageView) {
        into.tag = url
        cache.get(url)?.let { setAnimated(into, url, it); return }
        executor.execute {
            val bytes = runCatching { download(url) }.getOrNull() ?: return@execute
            cache.put(url, bytes)
            into.post { if (into.tag == url) setAnimated(into, url, bytes) }
        }
    }

    private fun setAnimated(view: ImageView, url: String, bytes: ByteArray) {
        if (view.tag != url) return
        val drawable = runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
        }.getOrNull() ?: return
        view.setImageDrawable(drawable)
        (drawable as? AnimatedImageDrawable)?.start()
    }

    private fun download(urlStr: String): ByteArray {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            instanceFollowRedirects = true
        }
        try {
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
