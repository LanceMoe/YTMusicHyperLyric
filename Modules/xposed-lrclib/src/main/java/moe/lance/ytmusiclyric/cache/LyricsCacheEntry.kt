package moe.lance.ytmusiclyric.cache

data class LyricsCacheEntry(
    val id: Long = 0L,
    val cacheKey: String,
    val title: String,
    val artist: String,
    val rawLrc: String,
    val source: String,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val hasLyrics: Boolean get() = rawLrc.isNotBlank()
    val displaySource: String get() = if (hasLyrics) source else "下载失败"
}
