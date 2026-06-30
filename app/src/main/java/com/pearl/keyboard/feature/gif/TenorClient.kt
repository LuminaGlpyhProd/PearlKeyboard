package com.pearl.keyboard.feature.gif

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * Tenor v2 client (#6). Implements search, trending (featured), categories and cursor
 * pagination per the official API. The key is injected (never hardcoded) — see
 * BuildConfig.TENOR_API_KEY / GifPanelView.
 *
 * All calls are blocking — invoke from a background thread. A [Page] carries the next
 * cursor for infinite scroll.
 */
class TenorClient(private val apiKey: String) {

    data class Gif(val previewUrl: String, val fullUrl: String, val description: String)
    data class Page(val gifs: List<Gif>, val next: String?)

    fun search(query: String, limit: Int = 24, pos: String? = null): Page =
        request("search", "&q=" + URLEncoder.encode(query, "UTF-8"), limit, pos)

    fun trending(limit: Int = 24, pos: String? = null): Page =
        request("featured", "", limit, pos)

    /** Tenor's suggested category search-terms (e.g. "excited", "#love"). */
    fun categories(): List<String> = runCatching {
        val url = "https://tenor.googleapis.com/v2/categories?key=$apiKey&client_key=pearl_keyboard"
        val tags = JSONObject(httpGet(url)).optJSONArray("tags") ?: return@runCatching emptyList<String>()
        (0 until tags.length()).mapNotNull { tags.getJSONObject(it).optString("searchterm").ifEmpty { null } }
    }.getOrDefault(emptyList())

    private fun request(endpoint: String, extra: String, limit: Int, pos: String?): Page {
        val url = "https://tenor.googleapis.com/v2/$endpoint" +
            "?key=$apiKey&client_key=pearl_keyboard&limit=$limit" +
            "&media_filter=tinygif,gif&contentfilter=medium$extra" +
            (if (pos != null) "&pos=$pos" else "")

        val obj = JSONObject(httpGet(url))
        val results = obj.optJSONArray("results") ?: return Page(emptyList(), null)
        val out = ArrayList<Gif>(results.length())
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val mf = r.optJSONObject("media_formats") ?: continue
            val preview = mf.optJSONObject("tinygif")?.optString("url").orEmpty()
            if (preview.isEmpty()) continue
            val full = mf.optJSONObject("gif")?.optString("url").orEmpty().ifEmpty { preview }
            out.add(Gif(preview, full, r.optString("content_description")))
        }
        val next = obj.optString("next").ifEmpty { null }
        return Page(out, next)
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PearlKeyboard")
        }
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
