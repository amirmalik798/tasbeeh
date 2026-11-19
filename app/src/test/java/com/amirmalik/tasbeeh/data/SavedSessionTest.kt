package com.amirmalik.tasbeeh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedSessionTest {

    @Test
    fun activeDurationAdvancesFromCurrentActiveSegment() {
        val active = session(startedAt = 10_000L, activeSegmentStartedAt = 10_000L)
        assertEquals(65_000L, active.durationAt(75_000L))
    }

    @Test
    fun pausedDurationDoesNotAdvanceWhilePaused() {
        val paused = session(
            startedAt = 10_000L,
            status = SessionStatus.PAUSED,
            durationMs = 30_000L,
            activeSegmentStartedAt = null,
            events = listOf(
                SessionEvent(SessionEventType.STARTED, 10_000L),
                SessionEvent(SessionEventType.PAUSED, 40_000L)
            )
        )
        assertEquals(30_000L, paused.durationAt(500_000L))
    }

    @Test
    fun resumeAndSecondPauseAccumulateOnlyActiveIntervals() {
        val secondPause = session(
            startedAt = 10_000L,
            status = SessionStatus.PAUSED,
            durationMs = 50_000L,
            activeSegmentStartedAt = null,
            events = listOf(
                SessionEvent(SessionEventType.STARTED, 10_000L),
                SessionEvent(SessionEventType.PAUSED, 40_000L),
                SessionEvent(SessionEventType.RESUMED, 100_000L),
                SessionEvent(SessionEventType.PAUSED, 120_000L)
            )
        )
        assertEquals(50_000L, secondPause.durationAt(900_000L))
        assertEquals(4, secondPause.events.size)
    }

    @Test
    fun finishingAfterResumeAddsOnlyTheLastActiveSegment() {
        val resumed = session(
            startedAt = 10_000L,
            status = SessionStatus.ACTIVE,
            durationMs = 30_000L,
            activeSegmentStartedAt = 100_000L,
            events = listOf(
                SessionEvent(SessionEventType.STARTED, 10_000L),
                SessionEvent(SessionEventType.PAUSED, 40_000L),
                SessionEvent(SessionEventType.RESUMED, 100_000L)
            )
        )
        val finished = finishSession(resumed, 130_000L, SessionStatus.COMPLETED)

        assertEquals(60_000L, finished.durationMs)
        assertEquals(130_000L, finished.endedAt)
        assertEquals(SessionEventType.ENDED, finished.events.last().type)
    }

    @Test
    fun multiplePauseResumeCyclesExcludeEveryPausedInterval() {
        val active = session(
            startedAt = 1_000L,
            status = SessionStatus.ACTIVE,
            durationMs = 50_000L,
            activeSegmentStartedAt = 200_000L,
            events = listOf(
                SessionEvent(SessionEventType.STARTED, 1_000L),
                SessionEvent(SessionEventType.PAUSED, 31_000L),
                SessionEvent(SessionEventType.RESUMED, 100_000L),
                SessionEvent(SessionEventType.PAUSED, 120_000L),
                SessionEvent(SessionEventType.RESUMED, 200_000L)
            )
        )
        assertEquals(70_000L, active.durationAt(220_000L))
    }

    @Test
    fun unknownLegacyStartTimeDoesNotInventDuration() {
        val legacy = session(startedAt = 0L, activeSegmentStartedAt = null)
        assertNull(legacy.durationAt(1_000_000L))
    }

    @Test
    fun finishingPreservesDhikrCountAndTargetSnapshot() {
        val active = session().copy(
            dhikr = "Ayat Ul Kursi",
            count = 47,
            target = 99,
            arabic = "Arabic snapshot"
        )
        val finished = finishSession(active, 200_000L, SessionStatus.COMPLETED)
        assertEquals("Ayat Ul Kursi", finished.dhikr)
        assertEquals(47, finished.count)
        assertEquals(99, finished.target)
        assertEquals("Arabic snapshot", finished.arabic)
    }

    @Test
    fun invalidStoredStatusRemainsBackwardCompatible() {
        assertEquals(SessionStatus.COMPLETED, SessionStatus.fromStoredValue("UNKNOWN"))
        assertEquals(SessionStatus.COMPLETED, SessionStatus.fromStoredValue(null))
    }

    private fun session(
        startedAt: Long = 10_000L,
        status: SessionStatus = SessionStatus.ACTIVE,
        durationMs: Long? = null,
        activeSegmentStartedAt: Long? = startedAt.takeIf { it > 0L },
        events: List<SessionEvent> = listOf(SessionEvent(SessionEventType.STARTED, startedAt)).filter { it.at > 0L }
    ) = SavedSession(
        id = "session-test",
        dhikr = "SubhanAllah",
        count = 7,
        target = 33,
        startedAt = startedAt,
        status = status,
        durationMs = durationMs,
        activeSegmentStartedAt = activeSegmentStartedAt,
        events = events
    )
}
