package com.pearl.keyboard.feature.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.pearl.keyboard.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updater (#14). Checks the GitHub Releases API for a newer version, and on
 * confirmation downloads the APK and launches Android's package installer.
 *
 * Always requires explicit user confirmation (Android security model). Only ever runs
 * from a settings Activity, never from the keyboard input path.
 */
object UpdateChecker {

    private const val LATEST = "https://api.github.com/repos/LuminaGlpyhProd/PearlKeyboard/releases/latest"

    /**
     * @param silent when true, says nothing if already up to date / on error
     *               (used for the automatic check on app launch).
     */
    fun checkAndPrompt(activity: Activity, silent: Boolean) {
        Thread {
            val result = runCatching {
                val obj = JSONObject(httpGet(LATEST))
                obj.optString("tag_name") to firstApkUrl(obj)
            }
            if (activity.isFinishing) return@Thread
            activity.runOnUiThread {
                result.onSuccess { (tag, apkUrl) ->
                    val newer = isNewer(tag.removePrefix("v"), BuildConfig.VERSION_NAME)
                    when {
                        newer && apkUrl != null -> showUpdateDialog(activity, tag, apkUrl)
                        !silent -> toast(activity, "You're on the latest version (${BuildConfig.VERSION_NAME})")
                    }
                }.onFailure {
                    if (!silent) toast(activity, "Update check failed: ${it.message}")
                }
            }
        }.start()
    }

    private fun showUpdateDialog(activity: Activity, tag: String, apkUrl: String) {
        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage("Version $tag is available (you have ${BuildConfig.VERSION_NAME}). Download and install now?")
            .setPositiveButton("Update") { _, _ -> downloadAndInstall(activity, apkUrl) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstall(activity: Activity, url: String) {
        toast(activity, "Downloading update…")
        Thread {
            val file = runCatching { download(activity, url) }.getOrNull()
            if (activity.isFinishing) return@Thread
            activity.runOnUiThread {
                if (file == null) toast(activity, "Download failed")
                else install(activity, file)
            }
        }.start()
    }

    private fun install(context: Context, file: File) {
        // The user must allow "install unknown apps" for us first.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            toast(context, "Allow installing apps for Pearl Keyboard, then tap Update again")
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { toast(context, "Couldn't open installer") }
    }

    // ---- helpers ----------------------------------------------------------

    private fun firstApkUrl(obj: JSONObject): String? {
        val assets = obj.optJSONArray("assets") ?: return null
        var fallback: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            val u = a.optString("browser_download_url")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            // Prefer the signed RELEASE apk — it shares the installed app's package id and
            // certificate, so Android applies it as an in-place update. Skip debug/unsigned
            // variants (different/ephemeral signatures would be rejected as a conflict).
            if (!name.contains("debug", true) && !name.contains("unsigned", true)) return u
            if (fallback == null) fallback = u
        }
        return fallback
    }

    /** Compare dotted version strings; true if [remote] > [local]. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "PearlKeyboard-Updater")
        }
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun download(context: Context, urlStr: String): File {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PearlKeyboard-Updater")
        }
        try {
            val file = File(context.cacheDir, "update.apk")
            conn.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            return file
        } finally {
            conn.disconnect()
        }
    }

    private fun toast(context: Context, msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
