package moe.lance.ytmusiclrc

internal object DurationMatcher {
    const val TOLERANCE_MS = 20_000L

    fun accepts(expectedMs: Long, actualMs: Long?): Boolean =
        expectedMs <= 0L || actualMs == null || actualMs <= 0L ||
            kotlin.math.abs(expectedMs - actualMs) <= TOLERANCE_MS
}
