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
    fun maleVoiceNameMatchesMalePreference() {
        assertEquals(true, arabicVoiceNameMatchesPreference("ar-male", "male"))
    }

    @Test
    fun femaleVoiceNameDoesNotMatchMalePreference() {
        assertEquals(false, arabicVoiceNameMatchesPreference("ar-female", "male"))
        assertEquals(false, arabicVoiceNameMatchesPreference("ar-woman", "male"))
    }

    @Test
    fun femaleVoiceNameMatchesFemalePreference() {
        assertEquals(true, arabicVoiceNameMatchesPreference("ar-female", "female"))
    }

    @Test
    fun voiceWithoutGenderMarkerIsTreatedAsUnknown() {
        org.junit.Assert.assertNull(arabicVoiceNameMatchesPreference("ar-standard", "male"))
    }

}
