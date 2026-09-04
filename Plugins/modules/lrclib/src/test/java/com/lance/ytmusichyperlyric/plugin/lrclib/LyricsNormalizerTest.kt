package com.lance.ytmusichyperlyric.plugin.lrclib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsNormalizerTest {
    @Test
    fun preservesRawTitleBeforeConservativeCandidate() {
        assertEquals(
            listOf("Song (Official Audio)", "Song"),
            LyricsNormalizer.titleCandidates("Song (Official Audio)"),
        )
    }

    @Test
    fun doesNotRemoveVersionInformation() {
        val candidates = LyricsNormalizer.titleCandidates("Song (Remastered)")
        assertTrue(candidates.contains("Song (Remastered)"))
        assertEquals("Song (Remastered)", LyricsNormalizer.normalizeTitle("Song (Remastered)"))
    }
}
