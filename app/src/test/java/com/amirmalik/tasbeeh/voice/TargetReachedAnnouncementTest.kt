package com.amirmalik.tasbeeh.voice

import com.amirmalik.tasbeeh.data.CountOutcome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetReachedAnnouncementTest {

    @Test
    fun onlyTheStoreTargetReachedOutcomeRequestsTheAnnouncement() {
        assertTrue(shouldAnnounceTargetReached(CountOutcome.TARGET_REACHED))
        assertFalse(shouldAnnounceTargetReached(CountOutcome.COUNTED))
        assertFalse(shouldAnnounceTargetReached(CountOutcome.PAUSED))
        assertFalse(shouldAnnounceTargetReached(CountOutcome.STALE_SELECTION))
    }
}
