package com.lance.ytmusichyperlyric.plugin.lrclib

/** Keeps the original title first, then offers a conservative search variant. */
internal object LyricsNormalizer {
    private val removableSuffix = Regex(
        "\\s*(?:[-|:]\\s*)?[\\(\\[]?(?:official audio|official video|music video|lyrics|lyric video|hd)[\\)\\]]?\\s*$",
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
        .trim('-', '|', ':')
        .trim()
}
