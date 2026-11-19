package com.amirmalik.tasbeeh.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleCounterValueTest {

    @Test
    fun incompleteSessionDoesNotRemainInTheNewCounter() {
        assertEquals(0, visibleCounterValue(18, SessionStatus.INCOMPLETE))
    }

    @Test
    fun activePausedAndCompletedCountsArePreserved() {
        assertEquals(5, visibleCounterValue(5, SessionStatus.ACTIVE))
        assertEquals(5, visibleCounterValue(5, SessionStatus.PAUSED))
        assertEquals(5, visibleCounterValue(5, SessionStatus.COMPLETED))
    }

    @Test
    fun countWithoutSessionIsPreservedForLegacyCounter() {
        assertEquals(7, visibleCounterValue(7, null))
    }

    @Test
    fun negativeStoredCountsAreClampedToZero() {
        assertEquals(0, visibleCounterValue(-5, null))
    }

    @Test
    fun safeIncrementAdvancesOrdinaryCounts() {
        assertEquals(1, incrementCounterValueSafely(0))
        assertEquals(42, incrementCounterValueSafely(41))
    }

    @Test
    fun safeIncrementDoesNotOverflowMaximumInteger() {
        assertEquals(Int.MAX_VALUE, incrementCounterValueSafely(Int.MAX_VALUE))
    }

    @Test
    fun safeIncrementRecoversFromNegativeStoredCount() {
        assertEquals(1, incrementCounterValueSafely(-1))
    }

    @Test
    fun fontScaleClampsValuesOutsideTheSupportedRange() {
        assertEquals(1.0f, safeAppFontScale(0.5f), 0.0f)
        assertEquals(1.25f, safeAppFontScale(2.0f), 0.0f)
    }

    @Test
    fun nonFiniteFontScaleFallsBackToTheSafeDefault() {
        assertEquals(DEFAULT_APP_FONT_SCALE, safeAppFontScale(Float.NaN), 0.0f)
        assertEquals(DEFAULT_APP_FONT_SCALE, safeAppFontScale(Float.POSITIVE_INFINITY), 0.0f)
        assertEquals(DEFAULT_APP_FONT_SCALE, safeAppFontScale(Float.NEGATIVE_INFINITY), 0.0f)
    }

    @Test
    fun duplicateCustomDhikrIdsAreRemovedFromDisplayData() {
        val first = CustomDhikr(id = "same", title = "First")
        val duplicate = CustomDhikr(id = "same", title = "Duplicate")
        val other = CustomDhikr(id = "other", title = "Other")

        assertEquals(
            listOf(first, other),
            uniqueCustomDhikrEntries(listOf(first, duplicate, other))
        )
    }
}
