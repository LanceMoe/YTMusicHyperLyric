package com.lance.ytmusichyperlyric.plugin.lrclib

import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
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
    ): FetchedLyrics? {
        if (Thread.currentThread().isInterrupted || remaining(deadlineMs) <= 0) return null

        val exactParams = buildList {
            add("track_name" to title)
            add("artist_name" to artist)
            album?.takeIf { it.isNotBlank() }?.let { add("album_name" to it) }
            durationMs?.takeIf { it > 0 }?.let { add("duration" to (it / 1_000.0).toString()) }
        }

        request(endpoint, exactParams, deadlineMs)
            ?.let { body -> JSONObject(body).optString("syncedLyrics").takeIf(::hasLyrics) }
            ?.let { return FetchedLyrics(it, source = "exact") }

        val searchEndpoint = searchEndpoint(endpoint) ?: return null
        val searchBody = request(
            searchEndpoint,
            listOf("q" to "$title $artist"),
            deadlineMs,
        ) ?: return null
        val candidate = parseSearchCandidates(searchBody)
            .let { LrclibSearchMatcher.select(it, title, artist, durationMs) }
            ?: return null
        return FetchedLyrics(candidate.syncedLyrics, source = "search")
    }

    private fun request(
        endpoint: String,
        params: List<Pair<String, String>>,
        deadlineMs: Long,
    ): String? {
        val query = params.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
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
            body
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

    private fun parseSearchCandidates(body: String): List<LrclibSearchCandidate> {
        val response = JSONArray(body)
        return buildList {
            for (index in 0 until response.length()) {
                val item = response.optJSONObject(index) ?: continue
                val syncedLyrics = item.optString("syncedLyrics").takeIf(::hasLyrics) ?: continue
                val trackName = item.optString("trackName").trim()
                val artistName = item.optString("artistName").trim()
                if (trackName.isEmpty() || artistName.isEmpty()) continue
                add(
                    LrclibSearchCandidate(
                        trackName = trackName,
                        artistName = artistName,
                        durationMs = item.optDouble("duration", Double.NaN)
                            .takeUnless { it.isNaN() || it <= 0.0 }
                            ?.times(1_000)
                            ?.toLong(),
                        syncedLyrics = syncedLyrics,
                    ),
                )
            }
        }
    }

    private fun searchEndpoint(endpoint: String): String? {
        val base = endpoint.substringBefore('?').trimEnd('/')
        return base.takeIf { it.endsWith("/get") }
            ?.removeSuffix("/get")
            ?.plus("/search")
    }

    private fun hasLyrics(value: String): Boolean = value.isNotBlank() && value != "null"

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun remaining(deadlineMs: Long): Long = deadlineMs - System.currentTimeMillis()
}

internal data class FetchedLyrics(
    val syncedLyrics: String,
    val source: String,
)
