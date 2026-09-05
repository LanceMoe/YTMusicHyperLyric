package moe.lance.ytmusiclrc

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/**
 * Client for fetching synced lyrics from Netease Cloud Music via public HTTPS endpoints.
 */
internal object NeteaseClient {
    private const val SEARCH_URL = "https://music.163.com/api/cloudsearch/pc"
    private const val LYRIC_URL = "https://music.163.com/api/song/lyric"
    private const val TIMEOUT_MS = 6_000

    fun fetch(title: String, artist: String, durationMs: Long): String? {
        val cleanTitle = LyricsNormalizer.cleanTitle(title, artist)
        val query = if (artist.isNotBlank()) "$cleanTitle $artist".trim() else cleanTitle
        val songs = search(query).ifEmpty {
            val simplifiedQuery = ChineseConverter.toSimplified(query)
            if (simplifiedQuery != query) search(simplifiedQuery) else emptyList()
        }
        if (songs.isEmpty()) return null

        val best = songs.best(title, artist, durationMs) ?: return null
        val lyric = getLyric(best.id) ?: return null
        if (lyric.isBlank() || !lyric.contains("[")) return null

        Log.i(LrclibXposedModule.TAG, "Netease hit: '${best.title}' — '${best.artist}' (id=${best.id})")
        return lyric
    }

    private fun search(query: String): List<SongCandidate> {
        val urlStr = "$SEARCH_URL?s=${URLEncoder.encode(query, Charsets.UTF_8.name())}&type=1&offset=0&limit=5"
        val body = httpGet(urlStr) ?: return emptyList()
        return runCatching {
            val json = JSONObject(body)
            val result = json.optJSONObject("result") ?: return emptyList()
            val songArray = result.optJSONArray("songs") ?: return emptyList()
            buildList {
                for (i in 0 until songArray.length()) {
                    val s = songArray.optJSONObject(i) ?: continue
                    val id = s.optLong("id")
                    if (id <= 0) continue
                    val name = s.optString("name").trim()
                    val arArray = s.optJSONArray("ar")
                    val artists = if (arArray != null && arArray.length() > 0) {
                        (0 until arArray.length()).mapNotNull { arArray.optJSONObject(it)?.optString("name") }.joinToString("/")
                    } else {
                        s.optJSONArray("artists")?.let { arr ->
                            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }.joinToString("/")
                        }.orEmpty()
                    }
                    val dt = s.optLong("dt", s.optLong("duration", 0L))
                    add(SongCandidate(id = id, title = name, artist = artists, durationMs = dt))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun getLyric(songId: Long): String? {
        val urlStr = "$LYRIC_URL?id=$songId&lv=1&kv=1&tv=-1"
        val body = httpGet(urlStr) ?: return null
        return runCatching {
            val json = JSONObject(body)
            val lrc = json.optJSONObject("lrc")?.optString("lyric")
            lrc?.takeIf { it.isNotBlank() && it != "null" }
        }.getOrNull()
    }

    private fun httpGet(urlStr: String): String? {
        val connection = (URL(urlStr).openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            connection.setRequestProperty("Referer", "https://music.163.com")
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

    private data class SongCandidate(val id: Long, val title: String, val artist: String, val durationMs: Long)

    private fun List<SongCandidate>.best(title: String, artist: String, duration: Long): SongCandidate? {
        val expectedTitle = ChineseConverter.normalize(LyricsNormalizer.cleanTitle(title, artist))
        val expectedArtist = ChineseConverter.normalize(artist)

        return mapNotNull { candidate ->
            val candidateTitle = ChineseConverter.normalize(candidate.title)
            val candidateArtist = ChineseConverter.normalize(candidate.artist)

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

