package com.amirmalik.tasbeeh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicSpeechQueueModeTest {

    @Test
    fun manualPlaybackReplacesPendingRecitations() {
        assertEquals(
            RECITATION_QUEUE_REPLACE,
            arabicSpeechQueueMode(manualPlayback = true)
        )
    }

    @Test
    fun countPlaybackAppendsToPendingRecitations() {
        assertEquals(
            RECITATION_QUEUE_APPEND,
            arabicSpeechQueueMode(manualPlayback = false)
        )
    }

    @Test
    fun automaticPlaybackIsAcceptedOnlyWhenThePlayerAndQueueAreIdle() {
        assertTrue(
            shouldAcceptRecitationRequest(
                queueMode = RECITATION_QUEUE_APPEND,
                hasActivePlayer = false,
                hasPendingRecitations = false
            )
        )
        assertFalse(
            shouldAcceptRecitationRequest(
                queueMode = RECITATION_QUEUE_APPEND,
                hasActivePlayer = true,
                hasPendingRecitations = false
            )
        )
        assertFalse(
            shouldAcceptRecitationRequest(
                queueMode = RECITATION_QUEUE_APPEND,
                hasActivePlayer = false,
                hasPendingRecitations = true
            )
        )
    }

    @Test
    fun manualPlaybackCanReplaceAnExistingPlayerAndQueue() {
        assertTrue(
            shouldAcceptRecitationRequest(
                queueMode = RECITATION_QUEUE_REPLACE,
                hasActivePlayer = true,
                hasPendingRecitations = true
            )
        )
    }

    @Test
    fun stopControlRemainsEnabledWhileRecitationIsPlaying() {
        assertTrue(
            arabicPlaybackControlEnabled(hasArabicText = false, isSpeaking = true)
        )
    }

    @Test
    fun playbackCannotStartWhenNoRecordedAudioIsAvailable() {
        assertFalse(
            arabicPlaybackControlEnabled(hasArabicText = false, isSpeaking = false)
        )
    }

    @Test
    fun playbackCanStartWhenRecordedAudioIsAvailable() {
        assertTrue(
            arabicPlaybackControlEnabled(hasArabicText = true, isSpeaking = false)
        )
    }
}
