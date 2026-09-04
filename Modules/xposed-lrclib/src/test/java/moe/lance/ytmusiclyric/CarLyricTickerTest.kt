package moe.lance.ytmusiclyric

import io.github.proify.lyricon.lyric.model.RichLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarLyricTickerTest {

    @Test
    fun formatsTitleOnlyModeCorrectly() {
        val origTitle = "晴天"
        val origArtist = "周杰伦"
        val origAlbum = "叶惠美"
        val line = RichLyricLine(begin = 1000, end = 4000, duration = 3000, text = "故事的小黄花")

        val (title, artist, album) = CarLyricTicker.formatMetadata(
            origTitle = origTitle,
            origArtist = origArtist,
            origAlbum = origAlbum,
            activeLine = line,
            mode = LyricDisplayMode.TITLE_ONLY,
        )

        assertEquals("故事的小黄花", title)
        assertEquals("周杰伦", artist)
        assertEquals("叶惠美", album)
    }

    @Test
    fun formatsTitleWithSongModeCorrectly() {
        val origTitle = "晴天"
        val origArtist = "周杰伦"
        val origAlbum = "叶惠美"
        val line = RichLyricLine(begin = 1000, end = 4000, duration = 3000, text = "从出生那年就飘着")

        val (title, artist, album) = CarLyricTicker.formatMetadata(
            origTitle = origTitle,
            origArtist = origArtist,
            origAlbum = origAlbum,
            activeLine = line,
            mode = LyricDisplayMode.TITLE_WITH_SONG,
        )

        assertEquals("晴天 - 从出生那年就飘着", title)
        assertEquals("周杰伦", artist)
        assertEquals("叶惠美", album)
    }

    @Test
    fun formatsArtistOnlyModeCorrectly() {
        val origTitle = "七里香"
        val origArtist = "周杰伦"
        val origAlbum = "七里香"
        val line = RichLyricLine(begin = 5000, end = 8000, duration = 3000, text = "雨下整夜 我的爱溢出就像雨水")

        val (title, artist, album) = CarLyricTicker.formatMetadata(
            origTitle = origTitle,
            origArtist = origArtist,
            origAlbum = origAlbum,
            activeLine = line,
            mode = LyricDisplayMode.ARTIST_ONLY,
        )

        assertEquals("七里香", title)
        assertEquals("雨下整夜 我的爱溢出就像雨水", artist)
        assertEquals("七里香", album)
    }

    @Test
    fun formatsAlbumOnlyModeCorrectly() {
        val origTitle = "安静"
        val origArtist = "周杰伦"
        val origAlbum = "范特西"
        val line = RichLyricLine(begin = 10000, end = 15000, duration = 5000, text = "只剩下钢琴陪我弹了一天")

        val (title, artist, album) = CarLyricTicker.formatMetadata(
            origTitle = origTitle,
            origArtist = origArtist,
            origAlbum = origAlbum,
            activeLine = line,
            mode = LyricDisplayMode.ALBUM_ONLY,
        )

        assertEquals("安静", title)
        assertEquals("周杰伦", artist)
        assertEquals("只剩下钢琴陪我弹了一天", album)
    }

    @Test
    fun restoresOriginalWhenActiveLineIsNull() {
        val origTitle = "枫"
        val origArtist = "周杰伦"
        val origAlbum = "11月的萧邦"

        val (title, artist, album) = CarLyricTicker.formatMetadata(
            origTitle = origTitle,
            origArtist = origArtist,
            origAlbum = origAlbum,
            activeLine = null,
            mode = LyricDisplayMode.TITLE_ONLY,
        )

        assertEquals("枫", title)
        assertEquals("周杰伦", artist)
        assertEquals("11月的萧邦", album)
    }

    @Test
    fun activeLineMatchingWithTimestamps() {
        val lines = listOf(
            RichLyricLine(begin = 2000, end = 5000, duration = 3000, text = "Line 1"),
            RichLyricLine(begin = 6000, end = 9000, duration = 3000, text = "Line 2"),
            RichLyricLine(begin = 10000, end = 15000, duration = 5000, text = "Line 3"),
        )

        // Before first line (intro)
        val at1000 = lines.firstOrNull { it.begin <= 1000 && 1000 < it.end }
        assertNull(at1000)

        // In line 1
        val at3000 = lines.firstOrNull { it.begin <= 3000 && 3000 < it.end }
        assertEquals("Line 1", at3000?.text)

        // In gap between line 1 and 2
        val at5500 = lines.firstOrNull { it.begin <= 5500 && 5500 < it.end }
        assertNull(at5500)

        // In line 2
        val at7000 = lines.firstOrNull { it.begin <= 7000 && 7000 < it.end }
        assertEquals("Line 2", at7000?.text)

        // After last line
        val at20000 = lines.firstOrNull { it.begin <= 20000 && 20000 < it.end }
        assertNull(at20000)
    }

    @Test
    fun configParsingHandlesDefaults() {
        val defaultCfg = CarBluetoothLyricConfig()
        assertEquals(true, defaultCfg.enabled)
        assertEquals(true, defaultCfg.onlyWhenBluetooth)
        assertEquals(LyricDisplayMode.TITLE_ONLY, defaultCfg.displayMode)
        assertEquals(0L, defaultCfg.offsetMs)
    }
}

