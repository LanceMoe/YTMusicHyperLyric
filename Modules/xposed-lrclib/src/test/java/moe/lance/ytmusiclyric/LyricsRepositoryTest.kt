package moe.lance.ytmusiclyric

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRepositoryTest {
    @Test
    fun fetchesLyricsForTraditionalJayChouTrack() {
        val lines = LyricsRepository.getLyrics("擱淺", "周杰倫", "七里香", 240_000L)
        assertNotNull("Should fetch lyrics for 擱淺 — 周杰倫", lines)
        assertTrue("Lines count should be > 10", lines!!.size > 10)
    }

    @Test
    fun fetchesLyricsForTrackWithOfficialMvSuffix() {
        val lines = LyricsRepository.getLyrics("告白气球 (Official MV)", "周杰伦", "周杰伦的床边故事", 215_000L)
        assertNotNull("Should fetch lyrics for 告白气球 (Official MV)", lines)
        assertTrue("Lines count should be > 10", lines!!.size > 10)
    }

    @Test
    fun fetchesLyricsForUgcUploadTrack() {
        // User's current playback: Title: "夜猫 - 张蔷", Uploader/Artist: "Far East Digger"
        val lines = LyricsRepository.getLyrics("夜猫 - 张蔷", "Far East Digger", "", 247_000L)
        assertNotNull("Should fetch lyrics for UGC upload: 夜猫 - 张蔷 / Far East Digger", lines)
        assertTrue("Lines count should be > 10", lines!!.size > 10)
    }
}
