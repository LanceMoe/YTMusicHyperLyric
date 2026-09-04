package moe.lance.ytmusiclyric

import android.util.Log
import io.github.proify.lyricon.lyric.model.RichLyricLine
import java.util.concurrent.ConcurrentHashMap

/**
 * Aggregator and coordinator for lyric providers.
 * Resolves title-artist pairs (including composite titles and title-only fallbacks),
 * and queries LRCLIB -> Netease Cloud Music -> Kugou Music.
 */
internal object LyricsRepository {
    private val cache = ConcurrentHashMap<String, List<RichLyricLine>>()

    fun getLyrics(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ): List<RichLyricLine>? {
        val cacheKey = buildCacheKey(title, artist, durationMs)
        cache[cacheKey]?.let { return it }

        val searchPairs = LyricsNormalizer.resolveSearchPairs(title, artist)
        for ((qTitle, qArtist) in searchPairs) {
            val lrc = fetchLrcFromProviders(qTitle, qArtist, album, durationMs) ?: continue
            val lines = LrcToLyricon.parse(lrc, durationMs)
            if (!lines.isNullOrEmpty()) {
                Log.i(
                    LrclibXposedModule.TAG,
                    "Found ${lines.size} lyric lines using query pair: title='$qTitle', artist='$qArtist'",
                )
                cache[cacheKey] = lines
                pruneCacheIfNeeded()
                return lines
            }
        }
        return null
    }

    private fun fetchLrcFromProviders(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ): String? {
        // 1. LRCLIB
        runCatching {
            LrclibClient.fetch(title, artist, album, durationMs)
        }.onFailure { error ->
            Log.w(LrclibXposedModule.TAG, "LRCLIB provider error for '$title' — '$artist'", error)
        }.getOrNull()?.let { return it }

        // 2. Netease Cloud Music Fallback
        Log.d(LrclibXposedModule.TAG, "Falling back to Netease for '$title' — '$artist'")
        runCatching {
            NeteaseClient.fetch(title, artist, durationMs)
        }.onFailure { error ->
            Log.w(LrclibXposedModule.TAG, "Netease provider error for '$title' — '$artist'", error)
        }.getOrNull()?.let { return it }

        // 3. Kugou Music Fallback
        Log.d(LrclibXposedModule.TAG, "Falling back to Kugou for '$title' — '$artist'")
        runCatching {
            KugouClient.fetch(title, artist, durationMs)
        }.onFailure { error ->
            Log.w(LrclibXposedModule.TAG, "Kugou provider error for '$title' — '$artist'", error)
        }.getOrNull()?.let { return it }

        return null
    }

    fun buildCacheKey(title: String, artist: String, durationMs: Long): String {
        val normTitle = ChineseConverter.normalize(LyricsNormalizer.cleanTitle(title, artist))
        val normArtist = ChineseConverter.normalize(artist)
        return "$normTitle\u0000$normArtist\u0000${durationMs / 5_000}"
    }

    private fun pruneCacheIfNeeded() {
        while (cache.size > 64) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
        }
    }
}
