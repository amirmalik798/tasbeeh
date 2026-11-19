package com.amirmalik.tasbeeh.voice

import java.util.ArrayDeque
import kotlin.math.sqrt

/**
 * Energy-based endpointing for short Dhikr phrases. This is intentionally
 * bounded: 20 ms frames, 80 ms pre-roll, a 600 ms trailing-silence endpoint,
 * and at most 8 seconds of audio per candidate utterance.
 */
internal class VoiceRepetitionSegmenter {
    private val preRoll = ArrayDeque<FloatArray>()
    private val currentFrames = ArrayList<FloatArray>()
    private var isRecording = false
    private var trailingSilenceSamples = 0
    private var speechSamples = 0
    private var totalSamples = 0

    fun acceptFrame(frame: FloatArray): FloatArray? {
        if (frame.isEmpty() || frame.size > MAX_FRAME_SAMPLES) return null
        val speech = rootMeanSquare(frame) >= SPEECH_RMS_THRESHOLD
        if (speech) {
            if (!isRecording) {
                isRecording = true
                while (preRoll.isNotEmpty()) currentFrames.add(preRoll.removeFirst())
            }
            currentFrames.add(frame.copyOf())
            speechSamples += frame.size
            totalSamples += frame.size
            trailingSilenceSamples = 0
        } else if (isRecording) {
            currentFrames.add(frame.copyOf())
            totalSamples += frame.size
            trailingSilenceSamples += frame.size
        } else {
            preRoll.addLast(frame.copyOf())
            while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
        }

        return if (isRecording &&
            (trailingSilenceSamples >= TRAILING_SILENCE_SAMPLES ||
                totalSamples >= MAX_UTTERANCE_SAMPLES)
        ) {
            finishCurrent()
        } else {
            null
        }
    }

    fun discardPending() {
        resetCurrent()
        preRoll.clear()
    }

    private fun finishCurrent(): FloatArray? {
        val result = if (speechSamples >= MIN_SPEECH_SAMPLES) {
            FloatArray(totalSamples).also { combined ->
                var offset = 0
                currentFrames.forEach { frame ->
                    frame.copyInto(combined, destinationOffset = offset)
                    offset += frame.size
                    frame.fill(0f)
                }
            }
        } else {
            currentFrames.forEach { it.fill(0f) }
            null
        }
        resetCurrent()
        return result
    }

    private fun resetCurrent() {
        currentFrames.forEach { it.fill(0f) }
        currentFrames.clear()
        isRecording = false
        trailingSilenceSamples = 0
        speechSamples = 0
        totalSamples = 0
    }

    private fun rootMeanSquare(samples: FloatArray): Float {
        var sum = 0.0
        samples.forEach { sample -> sum += sample.toDouble() * sample.toDouble() }
        return sqrt(sum / samples.size).toFloat()
    }

    private companion object {
        const val MAX_FRAME_SAMPLES = 640
        const val PRE_ROLL_FRAMES = 4
        const val TRAILING_SILENCE_SAMPLES = 9_600 // 600 ms at 16 kHz
        const val MIN_SPEECH_SAMPLES = 4_800 // 300 ms
        const val MAX_UTTERANCE_SAMPLES = 128_000 // 8 s
        const val SPEECH_RMS_THRESHOLD = 0.009f
    }
}
