package moe.lance.ytmusiclrc

import org.junit.Assert.*
import org.junit.Test

class DurationMatcherTest {
    @Test fun rejectsDifferentVersionsRegardlessOfMatchingNames() {
        assertFalse(DurationMatcher.accepts(180_000, 360_000))
        assertFalse(DurationMatcher.accepts(360_000, 180_000))
        assertFalse(DurationMatcher.accepts(180_000, 200_001))
        assertTrue(DurationMatcher.accepts(180_000, 200_000))
    }

    @Test fun missingDurationStillAllowsManualSearch() {
        assertTrue(DurationMatcher.accepts(0, 360_000))
        assertTrue(DurationMatcher.accepts(180_000, null))
        assertTrue(DurationMatcher.accepts(180_000, 0))
    }
}
