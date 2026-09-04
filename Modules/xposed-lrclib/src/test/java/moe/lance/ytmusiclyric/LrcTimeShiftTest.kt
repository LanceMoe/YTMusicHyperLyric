package moe.lance.ytmusiclyric

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcTimeShiftTest {
    @Test
    fun shiftsAllTimestampsAndPreservesMetadata() {
        val lrc = "[ar:歌手]\n[00:01.20][00:02.3]第一句\n[01:00.000]第二句"

        assertEquals(
            "[ar:歌手]\n[00:02.200][00:03.300]第一句\n[01:01.000]第二句",
            LrcTimeShift.apply(lrc, 1_000L),
        )
    }

    @Test
    fun negativeShiftClampsAtZero() {
        assertEquals("[00:00.000]第一句", LrcTimeShift.apply("[00:01.00]第一句", -2_000L))
    }
}
