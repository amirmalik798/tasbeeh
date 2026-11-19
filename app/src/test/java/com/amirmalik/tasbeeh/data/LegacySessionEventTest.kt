package com.amirmalik.tasbeeh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySessionEventTest {

    @Test
    fun countOnlyRecoveryIsAllowedOnlyWhenTheCurrentSessionRecordIsAbsent() {
        assertTrue(shouldUseLegacyCountOnlyRecovery("", false, 7))
        assertFalse(shouldUseLegacyCountOnlyRecovery("{corrupt", false, 7))
        assertFalse(shouldUseLegacyCountOnlyRecovery("", true, 7))
        assertFalse(shouldUseLegacyCountOnlyRecovery("", false, 0))
    }


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
    fun longRunningSessionTimelineStaysBoundedAndKeepsOriginalStartAndRecentEvents() {
        val events = (1..1_000).fold(
            listOf(SessionEvent(SessionEventType.STARTED, 100L))
        ) { timeline, index ->
            appendSessionEvent(
                timeline,
                SessionEvent(
                    if (index % 2 == 0) SessionEventType.RESUMED else SessionEventType.PAUSED,
                    100L + index
                )
            )
        }

        assertEquals(MAX_SESSION_TIMELINE_EVENTS, events.size)
        assertEquals(SessionEventType.STARTED, events.first().type)
        assertEquals(SessionEventType.RESUMED, events.last().type)
        assertEquals(1_100L, events.last().at)
        assertTrue(events.drop(1).all { it.at > events.first().at })
    }

    @Test
    fun timelineWithoutStartEventRetainsOnlyTheMostRecentBoundedEvents() {
        val events = (1..500).fold(emptyList<SessionEvent>()) { timeline, index ->
            appendSessionEvent(timeline, SessionEvent(SessionEventType.PAUSED, index.toLong()))
        }

        assertEquals(MAX_SESSION_TIMELINE_EVENTS, events.size)
        assertEquals(301L, events.first().at)
        assertEquals(500L, events.last().at)
    }

    @Test
    fun durationArithmeticSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, safeDurationSum(Long.MAX_VALUE - 5L, 10L))
        assertEquals(15L, safeDurationSum(10L, 5L))
        assertEquals(10L, safeDurationSum(-100L, 10L))
    }

    @Test
    fun activeDurationRemainsStableAfterEventHistoryIsCompacted() {
        val session = SavedSession(
            id = "bounded-session",
            dhikr = "SubhanAllah",
            count = 22,
            target = 33,
            startedAt = 100L,
            status = SessionStatus.ACTIVE,
            durationMs = 45_000L,
            activeSegmentStartedAt = 80_000L,
            events = (1..1_000).fold(
                listOf(SessionEvent(SessionEventType.STARTED, 100L))
            ) { timeline, index ->
                appendSessionEvent(
                    timeline,
                    SessionEvent(
                        if (index % 2 == 0) SessionEventType.RESUMED else SessionEventType.PAUSED,
                        100L + index
                    )
                )
            }
        )

        assertEquals(MAX_SESSION_TIMELINE_EVENTS, session.events.size)
        assertEquals(45_000L, accumulatedActiveDuration(session))
        assertEquals(50_000L, session.durationAt(85_000L))
    }

    @Test
    fun activeLegacySessionContainsOnlyKnownStartEvent() {
        assertEquals(
            listOf(SessionEvent(SessionEventType.STARTED, 100L)),
            legacySessionEvents(startedAt = 100L, endedAt = null)
        )
    }
}
