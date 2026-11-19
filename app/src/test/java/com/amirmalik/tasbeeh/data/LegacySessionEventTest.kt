package com.amirmalik.tasbeeh.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacySessionEventTest {

    @Test
    fun knownStartAndEndTimesProduceCompleteLegacyTimeline() {
        assertEquals(
            listOf(
                SessionEvent(SessionEventType.STARTED, 100L),
                SessionEvent(SessionEventType.ENDED, 250L)
            ),
            legacySessionEvents(startedAt = 100L, endedAt = 250L)
        )
    }

    @Test
    fun unknownStartTimeDoesNotInventStartEvent() {
        assertEquals(
            listOf(SessionEvent(SessionEventType.ENDED, 250L)),
            legacySessionEvents(startedAt = 0L, endedAt = 250L)
        )
    }

    @Test
    fun activeLegacySessionContainsOnlyKnownStartEvent() {
        assertEquals(
            listOf(SessionEvent(SessionEventType.STARTED, 100L)),
            legacySessionEvents(startedAt = 100L, endedAt = null)
        )
    }
}
