package moe.lance.ytmusiclrc

import io.github.proify.lyricon.lyric.model.RichLyricLine

internal object LrcToLyricon {
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val offset = Regex("^\\s*\\[offset\\s*:\\s*(-?\\d+)\\s*]\\s*$", RegexOption.IGNORE_CASE)

    fun parse(lrc: String, durationMs: Long): List<RichLyricLine>? {
        val offsetMs = lrc.lineSequence()
            .mapNotNull { offset.matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() }
            .firstOrNull() ?: 0
        val raw = buildList {
            lrc.lineSequence().forEach { line ->
                val matches = timestamp.findAll(line).toList()
                val text = matches.lastOrNull()?.let { line.substring(it.range.last + 1).trim() }.orEmpty()
                if (text.isBlank()) return@forEach
                matches.forEach { match ->
                    val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                    val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                    val fraction = match.groupValues[3]
                    val millis = when (fraction.length) {
                        1 -> fraction.toLong() * 100
                        2 -> fraction.toLong() * 10
                        3 -> fraction.toLong()
                        else -> 0
                    }
                    add(RawLine((minutes * 60_000 + seconds * 1_000 + millis + offsetMs).coerceAtLeast(0), text))
                }
            }
        }.sortedBy(RawLine::begin).distinctBy { it.begin to it.text }

        if (raw.isEmpty()) return null
        return raw.mapIndexed { index, line ->
            val next = raw.getOrNull(index + 1)?.begin
            val end = maxOf(next ?: durationMs.takeIf { it > line.begin } ?: (line.begin + 5_000), line.begin + 1)
            RichLyricLine(begin = line.begin, end = end, duration = end - line.begin, text = line.text)
        }
    }

    private data class RawLine(val begin: Long, val text: String)
}

