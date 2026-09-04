package moe.lance.ytmusiclyric

import android.content.Context
import android.util.Log
import io.github.proify.lyricon.lyric.model.RichLyricLine
import moe.lance.ytmusiclyric.cache.LyricsCacheClient
import moe.lance.ytmusiclyric.cache.LyricsCacheEntry
import java.util.concurrent.ConcurrentHashMap

/**
 * Aggregator and coordinator for lyric providers.
 * Resolves title-artist pairs (including composite titles and title-only fallbacks),
 * checks local persistent storage cache first (to avoid re-downloading),
 * and falls back to LRCLIB -> Netease Cloud Music -> Kugou Music.
 */
internal object LyricsRepository {
    private val cache = ConcurrentHashMap<String, List<RichLyricLine>>()

    fun getLyrics(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        context: Context? = null,
    ): List<RichLyricLine>? {
        val cacheKey = buildCacheKey(title, artist, durationMs)

        // 1. In-memory fast cache
        cache[cacheKey]?.let { return it }

        val cleanA = LyricsNormalizer.cleanArtist(artist)
        val cleanTitle = ChineseConverter.normalize(LyricsNormalizer.cleanTitle(title, cleanA))
        val cleanArtist = ChineseConverter.normalize(cleanA)

        // 2. Persistent local storage cache (if context available)
        if (context != null) {
            val localEntry = LyricsCacheClient.get(
                context = context,
                cacheKey = cacheKey,
                cleanTitle = cleanTitle,
                cleanArtist = cleanArtist,
                durationMs = durationMs,
            )
            if (localEntry != null && localEntry.rawLrc.isNotBlank()) {
                val lines = LrcToLyricon.parse(localEntry.rawLrc, durationMs)
                if (!lines.isNullOrEmpty()) {
                    Log.i(
                        LrclibXposedModule.TAG,
                        "Loaded ${lines.size} lyric lines from local cache for '$title' — '$artist' (source: ${localEntry.source})",
                    )
                    cache[cacheKey] = lines
                    pruneCacheIfNeeded()

                    // If cached entry had missing/zero duration, upgrade it in persistent DB
                    if (localEntry.durationMs <= 0L && durationMs > 0L) {
                        val upgradedEntry = localEntry.copy(
                            cacheKey = cacheKey,
                            durationMs = durationMs,
                            updatedAt = System.currentTimeMillis(),
                        )
                        LyricsCacheClient.save(context, upgradedEntry)
                        if (localEntry.cacheKey != cacheKey) {
                            LyricsCacheClient.delete(context, localEntry.cacheKey)
                        }
                    }

                    return lines
                }
            }
        }

        // 3. Fallback to network providers
        val searchPairs = LyricsNormalizer.resolveSearchPairs(title, artist)
        for ((qTitle, qArtist) in searchPairs) {
            val result = fetchLrcFromProviders(qTitle, qArtist, album, durationMs) ?: continue
            val (lrc, source) = result
            val lines = LrcToLyricon.parse(lrc, durationMs)
            if (!lines.isNullOrEmpty()) {
                Log.i(
                    LrclibXposedModule.TAG,
                    "Found ${lines.size} lyric lines from $source using query pair: title='$qTitle', artist='$qArtist'",
                )
                cache[cacheKey] = lines
                pruneCacheIfNeeded()

                // Save to local persistent cache
                if (context != null) {
                    LyricsCacheClient.save(
                        context,
                        LyricsCacheEntry(
                            cacheKey = cacheKey,
                            title = title,
                            artist = artist,
                            rawLrc = lrc,
                            source = source,
                            durationMs = durationMs,
                        ),
                    )
                }
                return lines
            }
        }
        // Keep unsuccessful songs available for manual searching and editing.
        // Empty records are not playable cache hits, so later playback still retries.
        if (context != null) {
            LyricsCacheClient.save(
                context,
                LyricsCacheEntry(
                    cacheKey = cacheKey,
                    title = title,
                    artist = artist,
                    rawLrc = "",
                    source = "下载失败",
                    durationMs = durationMs,
                ),
            )
        }
        return null
    }

    fun fetchRawFromProviders(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
    ): Pair<String, String>? {
        val searchPairs = LyricsNormalizer.resolveSearchPairs(title, artist)
        for ((qTitle, qArtist) in searchPairs) {
            val result = fetchLrcFromProviders(qTitle, qArtist, album, durationMs)
            if (result != null) return result
        }
        return null
    }

    private fun fetchLrcFromProviders(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ): Pair<String, String>? {
        // 1. LRCLIB
        runCatching {
            LrclibClient.fetch(title, artist, album, durationMs)
        }.onFailure { error ->
            Log.w(LrclibXposedModule.TAG, "LRCLIB provider error for '$title' — '$artist'", error)
        }.getOrNull()?.let { return Pair(it, "LRCLIB") }

        // 2. Netease Cloud Music Fallback
        Log.d(LrclibXposedModule.TAG, "Falling back to Netease for '$title' — '$artist'")
        runCatching {
            NeteaseClient.fetch(title, artist, durationMs)
        }.onFailure { error ->
            Log.w(LrclibXposedModule.TAG, "Netease provider error for '$title' — '$artist'", error)
        }.getOrNull()?.let { return Pair(it, "网易云") }

        // 3. Kugou Music Fallback
        Log.d(LrclibXposedModule.TAG, "Falling back to Kugou for '$title' — '$artist'")
        runCatching {
            KugouClient.fetch(title, artist, durationMs)
        }.onFailure { error ->
            Log.w(LrclibXposedModule.TAG, "Kugou provider error for '$title' — '$artist'", error)
        }.getOrNull()?.let { return Pair(it, "酷狗") }

        return null
    }

    fun buildCacheKeyPrefix(title: String, artist: String): String {
        val cleanA = LyricsNormalizer.cleanArtist(artist)
        val normTitle = ChineseConverter.normalize(LyricsNormalizer.cleanTitle(title, cleanA))
        val normArtist = ChineseConverter.normalize(cleanA)
        return "$normTitle\u0000$normArtist\u0000"
    }

    fun buildCacheKey(title: String, artist: String, durationMs: Long): String {
        val prefix = buildCacheKeyPrefix(title, artist)
        return "$prefix${durationMs / 5_000}"
    }

    fun evictFromMemory(cacheKey: String) {
        cache.remove(cacheKey)
    }

    fun clearMemoryCache() {
        cache.clear()
    }

    private fun pruneCacheIfNeeded() {
        while (cache.size > 64) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
        }
    }
}
