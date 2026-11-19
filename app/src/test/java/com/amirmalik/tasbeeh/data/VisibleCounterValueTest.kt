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
}
