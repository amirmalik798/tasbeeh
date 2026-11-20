package com.amirmalik.tasbeeh.voice

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRepetitionSegmenterTest {

    @Test
    fun segmenterWaitsForTrailingSilenceThenReturnsOneCompleteUtterance() {
        val segmenter = VoiceRepetitionSegmenter()
        repeat(4) { assertNull(segmenter.acceptFrame(FloatArray(320))) }
        repeat(22) { assertNull(segmenter.acceptFrame(FloatArray(320) { 0.12f })) }

        var completed: FloatArray? = null
        repeat(30) {
            val next = segmenter.acceptFrame(FloatArray(320))
            if (next != null) completed = next
        }

        assertNotNull(completed)
        assertTrue(completed!!.size >= 4 * 320 + 22 * 320 + 30 * 320)
        completed!!.fill(0f)
    }

    @Test
    fun segmenterDiscardsShortSpeechAndCanAcceptTheNextUtterance() {
        val segmenter = VoiceRepetitionSegmenter()
        repeat(4) { segmenter.acceptFrame(FloatArray(320)) }
        repeat(5) { segmenter.acceptFrame(FloatArray(320) { 0.12f }) }
        repeat(30) { assertNull(segmenter.acceptFrame(FloatArray(320))) }

        repeat(4) { segmenter.acceptFrame(FloatArray(320)) }
        repeat(22) { segmenter.acceptFrame(FloatArray(320) { 0.12f }) }
        var completed: FloatArray? = null
        repeat(30) {
            val next = segmenter.acceptFrame(FloatArray(320))
            if (next != null) completed = next
        }

        assertNotNull(completed)
        completed!!.fill(0f)
    }

    @Test
    fun discardPendingClearsBufferedSpeechWithoutProducingAnUtterance() {
        val segmenter = VoiceRepetitionSegmenter()
        repeat(4) { segmenter.acceptFrame(FloatArray(320)) }
        repeat(20) { segmenter.acceptFrame(FloatArray(320) { 0.1f }) }
        segmenter.discardPending()

        repeat(30) { assertNull(segmenter.acceptFrame(FloatArray(320))) }
    }
}
