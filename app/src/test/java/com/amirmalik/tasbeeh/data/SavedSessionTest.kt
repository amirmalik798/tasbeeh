package com.amirmalik.tasbeeh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedSessionTest {

    @Test
    fun totalDurationIsTheDifferenceBetweenStartAndEnd() {
        assertEquals(
            90_000L,
            sessionDurationMillis(
                startedAt = 10_000L,
                endedAt = 100_000L,
                now = 500_000L
            )
        )
    }

    @Test
    fun unfinishedSessionDurationRunsFromStartUntilNow() {
        assertEquals(
            65_000L,
            sessionDurationMillis(
                startedAt = 10_000L,
                endedAt = null,
                now = 75_000L
            )
        )
    }

    @Test
    fun pausedSessionUsesTheSameSimpleStartToEndDuration() {
        val paused = session(status = SessionStatus.PAUSED)
        assertEquals(120_000L, paused.durationAt(130_000L))
    }

    @Test
    fun finishingSessionStoresEndTimeAndTotalDuration() {
        val active = session(startedAt = 10_000L)
        val finished = finishSession(active, now = 130_000L, status = SessionStatus.INCOMPLETE)

        assertEquals(130_000L, finished.endedAt)
        assertEquals(120_000L, finished.durationMs)
        assertEquals(SessionStatus.INCOMPLETE, finished.status)
    }

    @Test
    fun completedSessionDurationDoesNotChangeAfterItEnds() {
        val completed = finishSession(
            session(startedAt = 10_000L),
            now = 130_000L,
            status = SessionStatus.COMPLETED
        )

        assertEquals(120_000L, completed.durationAt(900_000L))
        assertEquals(130_000L, completed.endedAt)
    }

    @Test
    fun durationIncludesTheTimeSpentPausedBetweenStartAndEnd() {
        // The simplified timing model intentionally measures wall-clock
        // start-to-end duration; pause/resume does not create extra timers.
        assertEquals(
            3_600_000L,
            sessionDurationMillis(
                startedAt = 1_000L,
                endedAt = 3_601_000L,
                now = 9_000_000L
            )
        )
    }

    @Test
    fun unknownLegacyStartTimeDoesNotInventADuration() {
        val recovered = session(startedAt = 0L)
        assertNull(recovered.durationAt(1_000_000L))
        assertNull(sessionDurationMillis(0L, 100_000L, 200_000L))
    }

    @Test
    fun finishingPreservesDhikrCountAndTargetSnapshot() {
        val active = session().copy(
            dhikr = "Ayat Ul Kursi",
            count = 47,
            target = 99,
            arabic = "Arabic snapshot"
        )
        val finished = finishSession(active, now = 200_000L, status = SessionStatus.COMPLETED)

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
        status: SessionStatus = SessionStatus.ACTIVE
    ) = SavedSession(
        id = "session-test",
        dhikr = "SubhanAllah",
        count = 7,
        target = 33,
        startedAt = startedAt,
        status = status
    )
}
