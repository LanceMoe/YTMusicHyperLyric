package moe.lance.ytmusiclrc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LrcToLyriconTest {
    @Test
    fun parsesStandardLrcLines() {
        val lrc = """
            [00:10.00]First line
            [00:20.50]Second line
            [00:35.123]Third line
        """.trimIndent()

        val lines = LrcToLyricon.parse(lrc, 40_000L)
        assertNotNull(lines)
        assertEquals(3, lines!!.size)
        assertEquals(10_000L, lines[0].begin)
        assertEquals(20_500L, lines[0].end)
        assertEquals("First line", lines[0].text)

        assertEquals(20_500L, lines[1].begin)
        assertEquals(35_123L, lines[1].end)
        assertEquals("Second line", lines[1].text)

        assertEquals(35_123L, lines[2].begin)
        assertEquals(40_000L, lines[2].end)
        assertEquals("Third line", lines[2].text)
    }

    @Test
    fun appliesOffsetCorrectly() {
        val lrc = """
            [offset:500]
            [00:10.00]Offset test
        """.trimIndent()

        val lines = LrcToLyricon.parse(lrc, 20_000L)
        assertNotNull(lines)
        assertEquals(1, lines!!.size)
        assertEquals(10_500L, lines[0].begin)
    }
}

