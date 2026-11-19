package com.amirmalik.tasbeeh.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object VoiceAudioRecorder {
    private const val SAMPLE_RATE = WhisperArabicRecognizer.SAMPLE_RATE
    private const val FRAME_SAMPLES = 320
    private const val MAX_ENROLLMENT_SECONDS = 8
    private const val MIN_ENROLLMENT_SAMPLES = SAMPLE_RATE / 2

    fun createRecorder(context: Context): AudioRecord {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Microphone permission is required for voice counting.")
        }
        val minimumBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimumBufferBytes <= 0) {
            throw IOException("This device does not provide a compatible microphone input.")
        }
        val bufferBytes = maxOf(minimumBufferBytes * 2, FRAME_SAMPLES * 2 * 4)
        return AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes
        ).also { recorder ->
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                throw IOException("The microphone could not be initialized.")
            }
        }
    }

    /**
     * Captures one short enrollment utterance in memory only. No file is created.
     * The caller must set stopRequested when the user presses Stop or leaves the editor.
     */
    suspend fun captureEnrollmentSample(
        context: Context,
        stopRequested: AtomicBoolean
    ): FloatArray = withContext(Dispatchers.IO) {
        val recorder = createRecorder(context)
        val maximumSamples = SAMPLE_RATE * MAX_ENROLLMENT_SECONDS
        val captured = FloatArray(maximumSamples)
        val frame = ShortArray(FRAME_SAMPLES)
        var written = 0
        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IOException("The microphone did not start recording.")
            }
            while (!stopRequested.get() && written < maximumSamples) {
                val read = recorder.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                if (read == AudioRecord.ERROR_DEAD_OBJECT ||
                    read == AudioRecord.ERROR_INVALID_OPERATION ||
                    read == AudioRecord.ERROR_BAD_VALUE
                ) {
                    throw IOException("Microphone capture was interrupted. Please try again.")
                }
                if (read > 0) {
                    for (index in 0 until read) {
                        captured[written + index] = frame[index] / 32768.0f
                    }
                    written += read
                }
            }
        } finally {
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            }
            recorder.release()
            frame.fill(0)
        }
        if (written < MIN_ENROLLMENT_SAMPLES) {
            captured.fill(0f)
            throw IOException("The recording was too short. Speak one complete Dhikr, then stop.")
        }
        captured.copyOf(written).also { captured.fill(0f) }
    }
}
