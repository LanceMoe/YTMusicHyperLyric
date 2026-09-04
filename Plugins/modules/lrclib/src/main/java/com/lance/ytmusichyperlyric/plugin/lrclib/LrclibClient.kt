package com.lance.ytmusichyperlyric.plugin.lrclib

import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

internal class LrclibClient(private val logger: PluginLogger) {
    private val connectTimeoutMs = 5_000
    private val readTimeoutMs = 10_000

    fun fetch(
        endpoint: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        deadlineMs: Long,
    ): String? {
        if (Thread.currentThread().isInterrupted || remaining(deadlineMs) <= 0) return null

        val params = buildList {
            add("track_name" to title)
            add("artist_name" to artist)
            album?.takeIf { it.isNotBlank() }?.let { add("album_name" to it) }
            durationMs?.takeIf { it > 0 }?.let { add("duration" to (it / 1_000.0).toString()) }
        }
        val query = params.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val url = if (endpoint.contains('?')) "$endpoint&$query" else "$endpoint?$query"
        var connection: HttpURLConnection? = null
        return try {
            val opened = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = minOf(connectTimeoutMs, remaining(deadlineMs).toInt().coerceAtLeast(1))
                readTimeout = minOf(readTimeoutMs, remaining(deadlineMs).toInt().coerceAtLeast(1))
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "YTMusicHyperLyric/0.1")
            }
            connection = opened
            if (opened.responseCode != HttpURLConnection.HTTP_OK) {
                logger.debug("LRCLIB 请求失败: code=${opened.responseCode}")
                return null
            }
            val body = opened.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (body.length > 1_000_000) {
                logger.warn("LRCLIB 响应过大，忽略")
                return null
            }
            JSONObject(body).optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        } catch (_: IOException) {
            logger.debug("LRCLIB 网络请求失败")
            null
        } catch (error: Exception) {
            logger.warn("LRCLIB 响应解析失败: ${error.javaClass.simpleName}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun remaining(deadlineMs: Long): Long = deadlineMs - System.currentTimeMillis()
}
