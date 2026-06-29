package com.pearl.keyboard.feature.gif

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * Minimal Tenor v2 client (#6). Returns preview (tinygif) + full (gif) URLs.
 * Requires an API key in [GifPanelView.TENOR_API_KEY]; with no key the panel never
 * constructs this and simply shows setup instructions.
 *
 * Network calls are blocking — always invoke from a background thread.
 */
class TenorClient(private val apiKey: String) {

    data class Gif(val previewUrl: String, val fullUrl: String, val description: String)

    fun search(query: String, limit: Int = 24): List<Gif> =
        request("search", "&q=" + URLEncoder.encode(query, "UTF-8"), limit)

    fun trending(limit: Int = 24): List<Gif> = request("featured", "", limit)

    private fun request(endpoint: String, extra: String, limit: Int): List<Gif> {
        val url = "https://tenor.googleapis.com/v2/$endpoint" +
            "?key=$apiKey&client_key=pearl_keyboard&limit=$limit" +
            "&media_filter=tinygif,gif&contentfilter=medium$extra"
        val json = httpGet(url)
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Gif>(results.length())
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val mf = r.optJSONObject("media_formats") ?: continue
            val preview = mf.optJSONObject("tinygif")?.optString("url").orEmpty()
            if (preview.isEmpty()) continue
            val full = mf.optJSONObject("gif")?.optString("url").orEmpty().ifEmpty { preview }
            out.add(Gif(preview, full, r.optString("content_description")))
        }
        return out
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
