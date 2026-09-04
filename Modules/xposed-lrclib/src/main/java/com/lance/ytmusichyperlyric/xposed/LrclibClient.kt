package com.lance.ytmusichyperlyric.xposed

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

internal object LrclibClient {
    private const val GET = "https://lrclib.net/api/get"
    private const val SEARCH = "https://lrclib.net/api/search"
    private const val TIMEOUT_MS = 6_000

    fun fetch(title: String, artist: String, album: String?, durationMs: Long): String? {
        val titleCandidates = LyricsNormalizer.titleCandidates(title, artist)
        val artistCandidates = LyricsNormalizer.artistCandidates(artist)

        // 1. Try exact get for candidate titles
        for (qTitle in titleCandidates) {
            for (qArtist in artistCandidates) {
                val lyrics = request(GET, buildList {
                    add("track_name" to qTitle)
                    add("artist_name" to qArtist)
                    album?.takeIf { it.isNotBlank() }?.let { add("album_name" to it) }
                    durationMs.takeIf { it > 0 }?.let { add("duration" to (it / 1_000.0).toString()) }
                })?.let { JSONObject(it).optString("syncedLyrics").takeIf(::hasLyrics) }

                if (lyrics != null) {
                    Log.i(LrclibXposedModule.TAG, "LRCLIB exact match hit: '$qTitle' — '$qArtist'")
                    return lyrics
                }
            }
        }

        // 2. Fallback to search query
        for (qTitle in titleCandidates.take(2)) {
            val qArtist = artistCandidates.firstOrNull() ?: artist
            val queryStr = if (qArtist.isNotBlank()) "$qTitle $qArtist" else qTitle
            val candidates = request(SEARCH, listOf("q" to queryStr))
                ?.let(::JSONArray)
                ?.let { response ->
                    buildList {
                        for (index in 0 until response.length()) {
                            val item = response.optJSONObject(index) ?: continue
                            val lyrics = item.optString("syncedLyrics").takeIf(::hasLyrics) ?: continue
                            add(
                                SearchCandidate(
                                    item.optString("trackName"),
                                    item.optString("artistName"),
                                    item.optDouble("duration", Double.NaN)
                                        .takeUnless(Double::isNaN)
                                        ?.times(1_000)
                                        ?.toLong(),
                                    lyrics,
                                ),
                            )
                        }
                    }
                }.orEmpty()

            val best = candidates.best(title, artist, durationMs)
            if (best != null) {
                Log.i(LrclibXposedModule.TAG, "LRCLIB search hit: '${best.title}' — '${best.artist}'")
                return best.lyrics
            }
        }

        return null
    }

    private fun request(endpoint: String, params: List<Pair<String, String>>): String? {
        val query = params.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
        val connection = (URL("$endpoint?$query").openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "YTMusicHyperLyric/0.3")
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

    private fun hasLyrics(value: String) = value.isNotBlank() && value != "null"

    private data class SearchCandidate(val title: String, val artist: String, val duration: Long?, val lyrics: String)

    private fun List<SearchCandidate>.best(title: String, artist: String, duration: Long): SearchCandidate? {
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
                val durationDiff = if (duration > 0 && candidate.duration != null) kotlin.math.abs(duration - candidate.duration) else null
                val durationScore = when {
                    durationDiff == null -> 0
                    durationDiff <= 3_000 -> 40
                    durationDiff <= 8_000 -> 25
                    durationDiff <= 20_000 -> 10
                    expectedArtist.isEmpty() -> -40
                    else -> -20
                }
                candidate to (titleScore + artistScore + durationScore)
            }
        }.filter { (_, score) -> score >= 115 }.maxByOrNull { (_, score) -> score }?.first
    }
}
