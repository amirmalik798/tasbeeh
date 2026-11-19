package com.amirmalik.tasbeeh.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * Offline multilingual Whisper recognizer. This deliberately uses the Whisper
 * decoder, not sherpa-onnx's transducer decoder.
 *
 * Instances are single-threaded: a service creates one instance and decodes
 * complete, endpointed utterances sequentially.
 */
internal class WhisperArabicRecognizer private constructor(
    private val recognizer: OfflineRecognizer
) : Closeable {

    fun transcribe(samples: FloatArray): String {
        if (samples.size !in MIN_SAMPLES..MAX_SAMPLES) {
            throw IOException("The spoken phrase was too short or too long. Please repeat it.")
        }
        if (samples.any { !it.isFinite() }) {
            throw IOException("The microphone produced invalid audio samples.")
        }

        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim().take(MAX_TRANSCRIPT_CHARS)
        } finally {
            stream.release()
        }
    }

    override fun close() {
        recognizer.release()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val MIN_SAMPLES = SAMPLE_RATE / 3
        private const val MAX_SAMPLES = SAMPLE_RATE * 9
        private const val MAX_TRANSCRIPT_CHARS = 1000

        fun create(modelFiles: VoiceModelRepository.ModelFiles): WhisperArabicRecognizer {
            if (!modelFiles.encoder.isFile || !modelFiles.decoder.isFile || !modelFiles.tokens.isFile) {
                throw IOException("Offline voice recognition is not installed. Open the Dhikr editor and set up voice counting.")
            }
            val model = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = modelFiles.encoder.absolutePath,
                    decoder = modelFiles.decoder.absolutePath,
                    language = "ar",
                    task = "transcribe",
                    tailPaddings = 1000,
                    enableTokenTimestamps = false,
                    enableSegmentTimestamps = false
                ),
                tokens = modelFiles.tokens.absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "whisper"
            )
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = model,
                decodingMethod = "greedy_search"
            )
            return WhisperArabicRecognizer(OfflineRecognizer(config = config))
        }
    }
}
