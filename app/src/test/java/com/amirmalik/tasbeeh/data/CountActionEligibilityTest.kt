package com.amirmalik.tasbeeh.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountActionEligibilityTest {

    @Test
    fun activeSessionCanBeUndoneWhenCountIsPositive() {
        assertTrue(canUndoCount(1, SessionStatus.ACTIVE))
    }

    @Test
    fun legacyCounterWithoutSessionCanBeUndone() {
        assertTrue(canUndoCount(12, null))
    }

    @Test
    fun zeroCountCannotBeUndone() {
        assertFalse(canUndoCount(0, SessionStatus.ACTIVE))
        assertFalse(canUndoCount(0, null))
    }

    @Test
    fun pausedSessionCannotBeUndone() {
        assertFalse(canUndoCount(4, SessionStatus.PAUSED))
    }

    @Test
    fun completedSessionCannotBeUndone() {
        assertFalse(canUndoCount(33, SessionStatus.COMPLETED))
    }

    @Test
    fun incompleteSessionCannotBeUndone() {
        assertFalse(canUndoCount(4, SessionStatus.INCOMPLETE))
    }
}
