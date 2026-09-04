package com.lance.ytmusichyperlyric.plugin.lrclib

import java.util.Locale
import kotlin.math.abs

internal data class LrclibSearchCandidate(
    val trackName: String,
    val artistName: String,
    val durationMs: Long?,
    val syncedLyrics: String,
)

/** Selects a search result conservatively: an exact/near-exact title and a related artist are required. */
internal object LrclibSearchMatcher {
    private const val MIN_ACCEPTED_SCORE = 120
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")

    fun select(
        candidates: List<LrclibSearchCandidate>,
        title: String,
        artist: String,
        durationMs: Long?,
    ): LrclibSearchCandidate? = candidates
        .map { candidate -> candidate to score(candidate, title, artist, durationMs) }
        .filter { (_, score) -> score >= MIN_ACCEPTED_SCORE }
        .maxByOrNull { (_, score) -> score }
        ?.first

    private fun score(
        candidate: LrclibSearchCandidate,
        title: String,
        artist: String,
        durationMs: Long?,
    ): Int {
        val titleScore = similarity(normalize(title), normalize(candidate.trackName), exact = 100, partial = 70)
        val artistScore = similarity(normalize(artist), normalize(candidate.artistName), exact = 60, partial = 45)
        if (titleScore == 0 || artistScore == 0) return 0

        val durationScore = when {
            durationMs == null || durationMs <= 0 || candidate.durationMs == null -> 0
            abs(durationMs - candidate.durationMs) <= 2_000L -> 30
            abs(durationMs - candidate.durationMs) <= 10_000L -> 20
            abs(durationMs - candidate.durationMs) <= 30_000L -> 5
            else -> -20
        }
        return titleScore + artistScore + durationScore
    }

    private fun similarity(expected: String, actual: String, exact: Int, partial: Int): Int = when {
        expected.isEmpty() || actual.isEmpty() -> 0
        expected == actual -> exact
        actual.contains(expected) || expected.contains(actual) -> partial
        else -> 0
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(nonWord, "")
}
