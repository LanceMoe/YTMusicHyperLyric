package moe.lance.ytmusiclyric

import moe.lance.ytmusiclyric.cache.LyricsCacheEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsCacheTest {

    @Test
    fun testCacheKeyConsistency() {
        val key1 = LyricsRepository.buildCacheKey("晴天 (Official Video)", "周杰伦 - Topic", 240_000L)
        val key2 = LyricsRepository.buildCacheKey("晴天", "周杰伦", 242_000L)
        // 240,000 / 5000 = 48, 242,000 / 5000 = 48
        assertEquals("Normalized cache keys for same song within bucket should match", key1, key2)
    }

    @Test
    fun testCacheKeyPrefixConsistency() {
        val zeroDurationKey = LyricsRepository.buildCacheKey("晴天 (Official Video)", "周杰伦 - Topic", 0L)
        val fullDurationKey = LyricsRepository.buildCacheKey("晴天", "周杰伦", 240_000L)
        val expectedPrefix = LyricsRepository.buildCacheKeyPrefix("晴天", "周杰伦")

        assertEquals("晴天\u0000周杰伦\u0000", expectedPrefix)
        assertEquals(expectedPrefix, zeroDurationKey.substringBeforeLast('\u0000') + "\u0000")
        assertEquals(expectedPrefix, fullDurationKey.substringBeforeLast('\u0000') + "\u0000")
    }

    @Test
    fun testCacheEntryModel() {
        val entry = LyricsCacheEntry(
            id = 1L,
            cacheKey = "晴天\u0000周杰伦\u000048",
            title = "晴天",
            artist = "周杰伦",
            rawLrc = "[00:10.00]故事的小黄花\n[00:15.00]从出生那年就飘着",
            source = "网易云",
            durationMs = 240_000L,
            updatedAt = 1700000000000L,
        )

        assertEquals("晴天", entry.title)
        assertEquals("周杰伦", entry.artist)
        assertEquals("网易云", entry.source)

        val parsed = LrcToLyricon.parse(entry.rawLrc, entry.durationMs)
        assertNotNull("Should parse valid cached LRC lines", parsed)
        assertEquals(2, parsed!!.size)
        assertEquals("故事的小黄花", parsed[0].text)
        assertEquals(10_000L, parsed[0].begin)
    }

    @Test
    fun testFetchRawFromProviders() {
        val result = LyricsRepository.fetchRawFromProviders("晴天", "周杰伦", "叶惠美", 269_000L)
        assertNotNull("Should fetch raw LRC from network providers", result)
        val (rawLrc, source) = result!!
        assertTrue("Source should be recognized", source in listOf("LRCLIB", "网易云", "酷狗"))
        assertTrue("Raw LRC should contain timestamp", rawLrc.contains("["))
    }
}

