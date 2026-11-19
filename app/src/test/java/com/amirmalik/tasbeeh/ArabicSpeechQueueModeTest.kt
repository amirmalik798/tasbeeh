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
}
