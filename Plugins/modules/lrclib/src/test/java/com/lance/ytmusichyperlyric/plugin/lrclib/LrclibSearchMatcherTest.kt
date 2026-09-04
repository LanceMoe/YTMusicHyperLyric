package com.lance.ytmusichyperlyric.plugin.lrclib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrclibSearchMatcherTest {
    @Test
    fun `selects same title artist and closest duration`() {
        val expected = LrclibSearchCandidate(
            trackName = "太陽與地球 (Sun & Earth)",
            artistName = "盧廣仲 (Crowd Lu)",
            durationMs = 262_000,
            syncedLyrics = "[00:00.00] lyrics",
        )
        val result = LrclibSearchMatcher.select(
            candidates = listOf(
                expected.copy(durationMs = 170_000),
                expected,
                expected.copy(trackName = "太陽與月亮"),
            ),
            title = "太陽與地球",
            artist = "盧廣仲",
            durationMs = 262_000,
        )

        assertEquals(expected, result)
    }

    @Test
    fun `rejects same title with an unrelated artist`() {
        val result = LrclibSearchMatcher.select(
            candidates = listOf(
                LrclibSearchCandidate(
                    trackName = "太陽與地球",
                    artistName = "另一位歌手",
                    durationMs = 262_000,
                    syncedLyrics = "[00:00.00] lyrics",
                ),
            ),
            title = "太陽與地球",
            artist = "盧廣仲",
            durationMs = 262_000,
        )

        assertNull(result)
    }
}
