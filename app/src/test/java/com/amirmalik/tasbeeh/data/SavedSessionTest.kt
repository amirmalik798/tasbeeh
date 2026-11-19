package com.amirmalik.tasbeeh.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedSessionTest {

    @Test
    fun activeDurationIncludesTheCurrentActiveSegment() {
        val session = session(
            lastActivityAt = 10_000L,
            activeDurationMs = 45_000L,
            status = SessionStatus.ACTIVE
        )

        assertEquals(55_000L, session.activeDurationAt(20_000L))
    }

    @Test
    fun activeDurationNeverCountsMoreThanTheInactivityWindow() {
        val session = session(
            lastActivityAt = 10_000L,
            activeDurationMs = 45_000L,
            status = SessionStatus.ACTIVE
        )

        assertEquals(
            45_000L + SESSION_IDLE_TIMEOUT_MS,
            session.activeDurationAt(10_000L + SESSION_IDLE_TIMEOUT_MS + 60_000L)
        )
    }

    @Test
    fun pausedSessionDoesNotAccumulateActiveTime() {
        val session = session(
            lastActivityAt = 10_000L,
            activeDurationMs = 180_000L,
            status = SessionStatus.PAUSED
        )

        assertEquals(180_000L, session.activeDurationAt(24L * 60L * 60L * 1000L))
    }

    @Test
    fun completedElapsedDurationIsStableAfterCompletion() {
        val session = session(
            startedAt = 1_000L,
            lastActivityAt = 30_000L,
            status = SessionStatus.COMPLETED,
            completedAt = 61_000L,
            endedAt = 61_000L
        )

        assertEquals(60_000L, session.totalElapsedAt(10_000_000L))
    }

    @Test
    fun incompleteElapsedDurationIncludesTheWholeSession() {
        val session = session(
            startedAt = 1_000L,
            lastActivityAt = 30_000L,
            status = SessionStatus.INCOMPLETE,
            endedAt = 91_000L
        )

        assertEquals(90_000L, session.totalElapsedAt(500_000L))
    }

    @Test
    fun activeElapsedDurationContinuesUntilTheCurrentTime() {
        val session = session(
            startedAt = 1_000L,
            lastActivityAt = 30_000L,
            status = SessionStatus.ACTIVE
        )

        assertEquals(59_000L, session.totalElapsedAt(60_000L))
    }

    @Test
    fun invalidStoredStatusFallsBackToCompletedForLegacyRecords() {
        assertEquals(SessionStatus.COMPLETED, SessionStatus.fromStoredValue("UNKNOWN"))
        assertEquals(SessionStatus.COMPLETED, SessionStatus.fromStoredValue(null))
    }

    private fun session(
        startedAt: Long = 1_000L,
        lastActivityAt: Long = 10_000L,
        activeDurationMs: Long? = 0L,
        status: SessionStatus = SessionStatus.ACTIVE,
        completedAt: Long? = null,
        endedAt: Long? = null
    ) = SavedSession(
        id = "session-test",
        dhikr = "SubhanAllah",
        count = 1,
        target = 33,
        startedAt = startedAt,
        lastActivityAt = lastActivityAt,
        activeDurationMs = activeDurationMs,
        status = status,
        completedAt = completedAt,
        endedAt = endedAt
    )
}
