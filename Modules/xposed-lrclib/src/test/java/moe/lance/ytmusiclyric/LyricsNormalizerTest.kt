package moe.lance.ytmusiclyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsNormalizerTest {
    @Test
    fun stripsOfficialAndMvSuffixes() {
        assertEquals("Cruel Summer", LyricsNormalizer.cleanTitle("Cruel Summer (Official Music Video)"))
        assertEquals("Cruel Summer", LyricsNormalizer.cleanTitle("Cruel Summer [Official MV]"))
        assertEquals("告白气球", LyricsNormalizer.cleanTitle("告白气球 (Official MV)"))
        assertEquals("搁浅", LyricsNormalizer.cleanTitle("搁浅 (Live)"))
        assertEquals("青花瓷", LyricsNormalizer.cleanTitle("青花瓷 【MV】"))
        assertEquals("Song Name", LyricsNormalizer.cleanTitle("Song Name - Official Video"))
    }

    @Test
    fun stripsArtistPrefixInTitle() {
        assertEquals("搁浅", LyricsNormalizer.cleanTitle("周杰伦 - 搁浅", "周杰伦"))
        assertEquals("擱淺", LyricsNormalizer.cleanTitle("周杰倫 - 擱淺", "周杰伦"))
        assertEquals("年少有为", LyricsNormalizer.cleanTitle("李荣浩 - 年少有为 (Official Video)", "李荣浩"))
    }

    @Test
    fun cleansTopicFromArtist() {
        assertEquals("Jay Chou", LyricsNormalizer.cleanArtist("Jay Chou - Topic"))
        assertEquals("DE DE MOUSE", LyricsNormalizer.cleanArtist("DE DE MOUSE - Topic"))
    }

    @Test
    fun resolvesSearchPairsForUgcUploads() {
        val pairs = LyricsNormalizer.resolveSearchPairs("夜猫 - 张蔷", "Far East Digger")
        assertTrue("Should extract (夜猫, 张蔷)", pairs.contains("夜猫" to "张蔷"))
        assertTrue("Should include pure title (夜猫, '')", pairs.contains("夜猫" to ""))
    }

    @Test
    fun generatesCandidatesWithTraditionalAndSimplified() {
        val candidates = LyricsNormalizer.titleCandidates("擱淺 (Official MV)", "周杰倫")
        assertTrue(candidates.contains("擱淺"))
        assertTrue(candidates.contains("搁浅"))
    }
}
