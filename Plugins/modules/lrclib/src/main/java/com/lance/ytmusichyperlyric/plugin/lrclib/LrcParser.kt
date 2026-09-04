package com.lance.ytmusichyperlyric.plugin.lrclib

import com.lidesheng.hyperlyric.plugin.api.PluginLyricLine

/** Converts LRC timestamps into the host's millisecond-based lyric DTO. */
internal object LrcParser {
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val offset = Regex("^\\s*\\[offset\\s*:\\s*(-?\\d+)\\s*]\\s*$", RegexOption.IGNORE_CASE)

    fun parse(value: String, durationMs: Long): List<PluginLyricLine>? {
        // LRC offset is expressed in milliseconds. Positive values move lyric timestamps later.
        val offsetMs = value.lineSequence()
            .mapNotNull { offset.matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() }
            .firstOrNull()
            ?: 0L
        val raw = buildList {
            value.lineSequence().forEach { line ->
                val matches = timestamp.findAll(line).toList()
                if (matches.isEmpty()) return@forEach
                val text = line.substring(matches.last().range.last + 1).trim()
                if (text.isBlank()) return@forEach
                matches.forEach { match ->
                    val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                    val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                    val fraction = match.groupValues[3]
                    val millis = when (fraction.length) {
                        1 -> fraction.toLong() * 100
                        2 -> fraction.toLong() * 10
                        3 -> fraction.toLong()
                        else -> 0L
                    }
                    val start = (minutes * 60_000 + seconds * 1_000 + millis + offsetMs)
                        .coerceAtLeast(0L)
                    add(RawLine(start, text))
                }
            }
        }.sortedWith(compareBy<RawLine> { it.begin }.thenBy { it.text })

        if (raw.isEmpty()) return null

        // Multiple timestamps for the same text are valid LRC. Merge equal starts so every
        // output row has a positive duration and the host receives a stable line index.
        val merged = raw.fold(mutableListOf<RawLine>()) { result, line ->
            val previous = result.lastOrNull()
            if (previous?.begin == line.begin) {
                if (!previous.text.split(" / ").contains(line.text)) {
                    result[result.lastIndex] = previous.copy(text = "${previous.text} / ${line.text}")
                }
            } else {
                result += line
            }
            result
        }

        return merged.mapIndexedNotNull { index, line ->
            val nextBegin = merged.getOrNull(index + 1)?.begin
            val fallbackEnd = durationMs.takeIf { it > line.begin } ?: line.begin + 5_000L
            val end = maxOf(nextBegin ?: fallbackEnd, line.begin + 1L)
            PluginLyricLine(
                begin = line.begin,
                end = end,
                duration = end - line.begin,
                text = line.text,
            )
        }
    }

    private data class RawLine(val begin: Long, val text: String)
}
