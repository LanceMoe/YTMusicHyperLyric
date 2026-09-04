package moe.lance.ytmusiclyric

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/**
 * Client for fetching synced lyrics from Kugou Music via public HTTPS endpoints.
 */
internal object KugouClient {
    private const val SEARCH_URL = "https://lyrics.kugou.com/search"
    private const val DOWNLOAD_URL = "https://lyrics.kugou.com/download"
    private const val TIMEOUT_MS = 6_000

    fun fetch(title: String, artist: String, durationMs: Long): String? {
        val cleanTitle = LyricsNormalizer.cleanTitle(title, artist)
        val query = if (artist.isNotBlank()) "$cleanTitle - $artist" else cleanTitle
        val candidates = search(query, durationMs).ifEmpty {
            val simplifiedQuery = ChineseConverter.toSimplified(query)
            if (simplifiedQuery != query) search(simplifiedQuery, durationMs) else emptyList()
        }
        if (candidates.isEmpty()) return null

        val best = candidates.best(title, artist, durationMs) ?: return null
        val lrc = downloadLyric(best.id, best.accessKey) ?: return null
        if (lrc.isBlank() || !lrc.contains("[")) return null

        Log.i(LrclibXposedModule.TAG, "Kugou hit: '${best.song}' — '${best.singer}' (id=${best.id})")
        return lrc
    }

    private fun search(keyword: String, durationMs: Long): List<Candidate> {
        val durParam = if (durationMs > 0) durationMs.toString() else ""
        val urlStr = "$SEARCH_URL?ver=1&man=yes&client=pc&keyword=${URLEncoder.encode(keyword, Charsets.UTF_8.name())}&duration=$durParam&hash="
        val body = httpGet(urlStr) ?: return emptyList()
        return runCatching {
            val json = JSONObject(body)
            val arr = json.optJSONArray("candidates") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    val id = c.optLong("id")
                    val accessKey = c.optString("accesskey")
                    if (id <= 0 || accessKey.isBlank()) continue
                    val song = c.optString("song").trim()
                    val singer = c.optString("singer").trim()
                    val duration = c.optLong("duration", 0L)
                    add(Candidate(id = id, accessKey = accessKey, song = song, singer = singer, durationMs = duration))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun downloadLyric(id: Long, accessKey: String): String? {
        val urlStr = "$DOWNLOAD_URL?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8"
        val body = httpGet(urlStr) ?: return null
        return runCatching {
            val json = JSONObject(body)
            val contentB64 = json.optString("content")
            if (contentB64.isBlank()) null
            else String(java.util.Base64.getDecoder().decode(contentB64.replace("\n", "").replace("\r", "")), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun httpGet(urlStr: String): String? {
        val connection = (URL(urlStr).openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText().takeIf { body -> body.length <= 1_000_000 }
            }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private data class Candidate(
        val id: Long,
        val accessKey: String,
        val song: String,
        val singer: String,
        val durationMs: Long,
    )

    private fun List<Candidate>.best(title: String, artist: String, duration: Long): Candidate? {
        val expectedTitle = ChineseConverter.normalize(LyricsNormalizer.cleanTitle(title, artist))
        val expectedArtist = ChineseConverter.normalize(artist)

        return mapNotNull { candidate ->
            val candidateTitle = ChineseConverter.normalize(candidate.song)
            val candidateArtist = ChineseConverter.normalize(candidate.singer)

            val titleScore = when {
                candidateTitle == expectedTitle -> 100
                candidateTitle.contains(expectedTitle) || expectedTitle.contains(candidateTitle) -> 70
                else -> 0
            }
            val artistScore = when {
                expectedArtist.isEmpty() -> 50
                candidateArtist == expectedArtist -> 60
                candidateArtist.contains(expectedArtist) || expectedArtist.contains(candidateArtist) -> 45
                else -> 0
            }
            if (titleScore == 0 || artistScore == 0) null
            else {
                val durationDiff = if (duration > 0 && candidate.durationMs > 0) kotlin.math.abs(duration - candidate.durationMs) else null
                val durationScore = when {
                    durationDiff == null -> 0
                    durationDiff <= 3_000 -> 40
                    durationDiff <= 8_000 -> 25
                    durationDiff <= 20_000 -> 10
                    expectedArtist.isEmpty() -> -40 // In title-only search, heavily penalize duration mismatch
                    else -> -20
                }
                candidate to (titleScore + artistScore + durationScore)
            }
        }.filter { (_, score) -> score >= 115 }.maxByOrNull { (_, score) -> score }?.first
    }
}
