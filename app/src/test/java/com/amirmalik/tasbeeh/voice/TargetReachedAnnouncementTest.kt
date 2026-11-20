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
    fun aPersistedSessionIDPreventsRepeatingTheAlertAfterActivityRecreation() {
        val sessionId = "3e6f0b66-29c6-4f29-959f-9b54c1f93b4a"
        val persisted = updatedAnnouncedSessionIds(emptySet(), sessionId)

        assertTrue(sessionId in persisted)
        assertFalse(shouldAnnounceSession(sessionId, persisted.toSet()))
    }

    @Test
    fun differentCompletedSessionsCanEachAnnounceOnce() {
        val first = "3e6f0b66-29c6-4f29-959f-9b54c1f93b4a"
        val second = "a9dbbf32-12d5-44d7-b58e-f102b1ef24f5"
        val afterFirst = updatedAnnouncedSessionIds(emptySet(), first)

        assertFalse(shouldAnnounceSession(first, afterFirst))
        assertTrue(shouldAnnounceSession(second, afterFirst))
    }

    @Test
    fun persistedDeduplicationSetRemainsBounded() {
        val previous = (1..70).map { "session-$it" }.toSet()
        val updated = updatedAnnouncedSessionIds(previous, "latest-session", maxEntries = 64)

        assertEquals(64, updated.size)
        assertTrue("latest-session" in updated)
    }

    @Test
    fun notificationIDIsStableForOneSession() {
        val sessionId = "3e6f0b66-29c6-4f29-959f-9b54c1f93b4a"
        assertEquals(notificationIdForSession(sessionId), notificationIdForSession(sessionId))
    }

    @Test
    fun onlyTheStoreTargetReachedOutcomeRequestsTheAnnouncement() {
        assertTrue(shouldAnnounceTargetReached(CountOutcome.TARGET_REACHED))
        assertFalse(shouldAnnounceTargetReached(CountOutcome.COUNTED))
        assertFalse(shouldAnnounceTargetReached(CountOutcome.PAUSED))
        assertFalse(shouldAnnounceTargetReached(CountOutcome.STALE_SELECTION))
    }
}
