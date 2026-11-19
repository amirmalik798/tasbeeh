package com.amirmalik.tasbeeh

import android.speech.tts.TextToSpeech
import org.junit.Assert.assertEquals
import org.junit.Test

class ArabicSpeechQueueModeTest {

    @Test
    fun manualPlaybackReplacesPendingCountSpeech() {
        assertEquals(
            TextToSpeech.QUEUE_FLUSH,
            arabicSpeechQueueMode(manualPlayback = true)
        )
    }

    @Test
    fun countPlaybackAppendsToPendingSpeech() {
        assertEquals(
            TextToSpeech.QUEUE_ADD,
            arabicSpeechQueueMode(manualPlayback = false)
        )
    }
    @Test
    fun playbackCanBeStoppedWhenCurrentDhikrHasNoArabicText() {
        org.junit.Assert.assertTrue(
            arabicPlaybackControlEnabled(hasArabicText = false, isSpeaking = true)
        )
    }

    @Test
    fun playbackCannotStartWhenNoArabicTextIsAvailable() {
        org.junit.Assert.assertFalse(
            arabicPlaybackControlEnabled(hasArabicText = false, isSpeaking = false)
        )
    }

    @Test
    fun playbackCanStartWhenArabicTextIsAvailable() {
        org.junit.Assert.assertTrue(
            arabicPlaybackControlEnabled(hasArabicText = true, isSpeaking = false)
        )
    }

    @Test
    fun preferredVoiceMatchingDoesNotConfuseMaleWithFemaleNameTokens() {
        assertEquals(true, arabicVoiceNameMatchesPreference("ar-female", "male"))
        assertEquals(false, arabicVoiceNameMatchesPreference("ar-female", "female").not())
        assertEquals(true, arabicVoiceNameMatchesPreference("ar-male", "male"))
        assertEquals(false, arabicVoiceNameMatchesPreference("ar-woman", "male"))
    }

}
