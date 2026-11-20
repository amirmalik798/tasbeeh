package com.amirmalik.tasbeeh.voice

import com.amirmalik.tasbeeh.data.CountOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetReachedAnnouncementTest {

    @Test
    fun spokenAnnouncementUsesTheRequestedExactPhrase() {
        assertEquals("TARGET REACHED", TARGET_REACHED_ANNOUNCEMENT)
    }

    @Test
    fun onlyTheStoreTargetReachedOutcomeRequestsTheAnnouncement() {
        assertTrue(shouldAnnounceTargetReached(CountOutcome.TARGET_REACHED))
        assertFalse(shouldAnnounceTargetReached(CountOutcome.COUNTED))
        assertFalse(shouldAnnounceTargetReached(CountOutcome.PAUSED))
        assertFalse(shouldAnnounceTargetReached(CountOutcome.STALE_SELECTION))
    }
}
