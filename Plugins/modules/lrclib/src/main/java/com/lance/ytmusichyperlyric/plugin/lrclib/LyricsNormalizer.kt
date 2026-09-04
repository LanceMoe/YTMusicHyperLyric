package com.lance.ytmusichyperlyric.plugin.lrclib

/** Keeps the original title first, then offers a clean search variant. */
internal object LyricsNormalizer {
    private val removableSuffix = Regex(
        "\\s*(?:[-|:]\\s*)?[\\(\\[\\{【（]?(?:official\\s+(?:music\\s+video|video|audio|mv|visualizer)|music\\s+video|official\\s+lyrics?|lyric\\s+video|lyrics?|audio|visualizer|mv|hd|4k|1080p|live|现场版)[\\)\\]\\}】）]?\\s*$",
        RegexOption.IGNORE_CASE,
    )

    fun titleCandidates(raw: String): List<String> {
        val original = raw.trim()
        if (original.isEmpty()) return emptyList()
        val normalized = normalizeTitle(original)
        return listOf(original, normalized).distinct().filter { it.isNotEmpty() }
    }

    fun normalizeTitle(raw: String): String = raw
        .trim()
        .replace(removableSuffix, "")
        .trim()
        .trim('-', '|', ':', '—')
        .trim()
}
