package com.lance.ytmusichyperlyric.plugin.lrclib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesMillisecondsAndCalculatesDurations() {
        val result = LrcParser.parse(
            "[00:01.50]First line\n[00:03.000]Second line",
            durationMs = 5_000L,
        )

        requireNotNull(result)
        assertEquals(2, result.size)
        assertEquals(1_500L, result[0].begin)
        assertEquals(3_000L, result[0].end)
        assertEquals(1_500L, result[0].duration)
        assertEquals(5_000L, result[1].end)
    }

    @Test
    fun appliesGlobalOffsetAndMergesDuplicateTimestamps() {
        val result = LrcParser.parse(
            "[offset:-500]\n[00:01.00]First\n[00:01.00]Second\n[00:03.00]Third",
            durationMs = 5_000L,
        )

        requireNotNull(result)
        assertEquals(2, result.size)
        assertEquals(500L, result[0].begin)
        assertEquals("First / Second", result[0].text)
        assertEquals(2_000L, result[0].duration)
    }

    @Test
    fun rejectsMetadataOnlyOrUntimestampedLyrics() {
        assertNull(LrcParser.parse("[ar:artist]\nplain lyrics", 0L))
    }
}
