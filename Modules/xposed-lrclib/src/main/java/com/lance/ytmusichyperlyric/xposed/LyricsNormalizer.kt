package com.lance.ytmusichyperlyric.xposed

/**
 * Normalizes YouTube Music track metadata by stripping video/release annotations,
 * handling artist-title composite formats, and producing ordered search candidates.
 */
internal object LyricsNormalizer {
    private val BRACKETED_PATTERNS = listOf(
        // MV / Video / Audio annotations
        Regex(
            "[\\(\\[\\{【（]\\s*(?:official\\s+(?:music\\s+video|video|audio|mv|visualizer)|music\\s+video|official\\s+lyrics?|lyric\\s+video|lyrics?|audio|visualizer|mv|hd|4k|1080p)\\s*[\\)\\]\\}】）]",
            RegexOption.IGNORE_CASE,
        ),
        // Live / Remastered annotations
        Regex(
            "[\\(\\[\\{【（]\\s*(?:live(?:\\s+at\\b.*)?|live\\s+version|现场版?|演唱会版?|remaster(?:ed)?(?:\\s*\\d{4})?|重置版)\\s*[\\)\\]\\}】）]",
            RegexOption.IGNORE_CASE,
        ),
        // Feat / Ft annotations
        Regex(
            "[\\(\\[\\{【（]\\s*(?:feat\\.|ft\\.|featuring)\\s+[^\\)\\]\\}】）]+[\\)\\]\\}】）]",
            RegexOption.IGNORE_CASE,
        ),
    )

    private val TAILING_OFFICIAL_SUFFIX = Regex(
        "\\s*[-|:]\\s*(?:official\\s+(?:video|music\\s*video|audio|mv)|music\\s+video|mv|lyrics?)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    private val ARTIST_TOPIC_SUFFIX = Regex("\\s*-\\s*topic$", RegexOption.IGNORE_CASE)

    /**
     * Cleans up uploader / channel metadata (e.g. removing " - Topic").
     */
    fun cleanArtist(rawArtist: String): String {
        return ARTIST_TOPIC_SUFFIX.replace(rawArtist.trim(), "").trim()
    }

    /**
     * Cleans up common noisy YouTube Music titles into a clean track name.
     */
    fun cleanTitle(rawTitle: String, artist: String = ""): String {
        var title = rawTitle.trim()

        // 1. If title starts with "$artist - " or ends with " - $artist", strip the redundant artist
        if (artist.isNotBlank()) {
            val normalizedArtist = ChineseConverter.normalize(artist)
            val prefixRegex = Regex("^([^\\-]+)\\s*-\\s*(.+)$")
            prefixRegex.matchEntire(title)?.let { match ->
                val part1 = match.groupValues[1].trim()
                val part2 = match.groupValues[2].trim()
                if (ChineseConverter.normalize(part1) == normalizedArtist) {
                    title = part2
                } else if (ChineseConverter.normalize(part2) == normalizedArtist) {
                    title = part1
                }
            }
        }

        // 2. Strip trailing official/MV markers
        title = TAILING_OFFICIAL_SUFFIX.replace(title, "").trim()

        // 3. Strip bracketed annotations
        for (pattern in BRACKETED_PATTERNS) {
            title = pattern.replace(title, " ").trim()
        }

        // 4. Clean leading/trailing punctuation and extra whitespace
        title = title.trim('-', '|', ':', '—', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        return title
    }

    /**
     * Generates a list of (title, artist) query pairs to handle both standard songs
     * and UGC/video uploads where the artist is an uploader or the title is composite (e.g. "夜猫 - 张蔷").
     */
    fun resolveSearchPairs(rawTitle: String, rawArtist: String): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        val cleanArtist = cleanArtist(rawArtist)
        val cleanTitle = cleanTitle(rawTitle, cleanArtist)

        // 1. Check for composite delimiters in title (e.g. "夜猫 - 张蔷" or "张蔷 - 夜猫")
        val delimiterRegex = Regex("\\s*[-—/|]\\s*")
        val splitParts = cleanTitle.split(delimiterRegex).filter { it.isNotBlank() }
        if (splitParts.size == 2) {
            val part1 = splitParts[0].trim()
            val part2 = splitParts[1].trim()
            // Assume Part1 is Title, Part2 is Artist (e.g. "夜猫 - 张蔷")
            pairs.add(part1 to part2)
            // Assume Part2 is Title, Part1 is Artist (e.g. "张蔷 - 夜猫")
            pairs.add(part2 to part1)
            // Pure title from part1
            pairs.add(part1 to "")
            // Pure title from part2
            pairs.add(part2 to "")
        }

        // 2. Original cleaned title with cleaned artist
        pairs.add(cleanTitle to cleanArtist)

        // 3. Original cleaned title alone (pure title search fallback)
        pairs.add(cleanTitle to "")

        // Deduplicate while preserving order
        val seen = mutableSetOf<Pair<String, String>>()
        val unique = mutableListOf<Pair<String, String>>()
        for (pair in pairs) {
            if (pair.first.isNotBlank() && seen.add(pair)) {
                unique.add(pair)
            }
        }
        return unique
    }

    /**
     * Generates an ordered list of title candidates for querying lyric services.
     */
    fun titleCandidates(rawTitle: String, artist: String = ""): List<String> {
        val original = rawTitle.trim()
        if (original.isEmpty()) return emptyList()

        val cleaned = cleanTitle(original, artist)
        val originalSimplified = ChineseConverter.toSimplified(original)
        val cleanedSimplified = ChineseConverter.toSimplified(cleaned)

        return listOf(cleaned, cleanedSimplified, original, originalSimplified)
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Generates artist candidates (e.g. original and simplified).
     */
    fun artistCandidates(artist: String): List<String> {
        val original = cleanArtist(artist)
        if (original.isEmpty()) return emptyList()
        val simplified = ChineseConverter.toSimplified(original)
        return listOf(original, simplified).filter { it.isNotBlank() }.distinct()
    }
}
