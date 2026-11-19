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
}
