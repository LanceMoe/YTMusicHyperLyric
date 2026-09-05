package moe.lance.ytmusiclrc

import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseConverterTest {
    @Test
    fun convertsTraditionalToSimplified() {
        val traditional = "擱淺 周杰倫 愛在西元前 年少有爲 說好不哭 聽媽媽的話 楓 軌跡 體面 晴天"
        val expected = "搁浅 周杰伦 爱在西元前 年少有为 说好不哭 听妈妈的话 枫 轨迹 体面 晴天"
        assertEquals(expected, ChineseConverter.toSimplified(traditional))
    }

    @Test
    fun normalizesEquivalently() {
        val trad = "  [MV] 擱淺 (Live) - 周杰倫!  "
        val simp = "mv 搁浅 live 周杰伦"
        assertEquals(ChineseConverter.normalize(simp), ChineseConverter.normalize(trad))
    }
}

