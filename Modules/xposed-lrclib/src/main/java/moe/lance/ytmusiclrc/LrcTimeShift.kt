package moe.lance.ytmusiclrc

/** Applies one global time shift to every LRC timestamp while leaving metadata intact. */
object LrcTimeShift {
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun apply(lrc: String, deltaMs: Long): String {
        if (deltaMs == 0L) return lrc

        return timestamp.replace(lrc) { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return@replace match.value
            val seconds = match.groupValues[2].toLongOrNull() ?: return@replace match.value
            val fraction = match.groupValues[3]
            val millis = when (fraction.length) {
                1 -> fraction.toLong() * 100
                2 -> fraction.toLong() * 10
                3 -> fraction.toLong()
                else -> 0L
            }
            val shifted = (minutes * 60_000L + seconds * 1_000L + millis + deltaMs).coerceAtLeast(0L)
            val shiftedMinutes = shifted / 60_000L
            val shiftedSeconds = (shifted % 60_000L) / 1_000L
            val shiftedMillis = shifted % 1_000L
            "[%02d:%02d.%03d]".format(shiftedMinutes, shiftedSeconds, shiftedMillis)
        }
    }
}
