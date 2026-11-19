package com.amirmalik.tasbeeh.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.amirmalik.tasbeeh.R
import com.amirmalik.tasbeeh.data.CountOutcome
import com.amirmalik.tasbeeh.data.CustomDhikr
import com.amirmalik.tasbeeh.data.TasbeehStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal enum class VoiceServiceState {
    IDLE,
    PREPARING,
    LISTENING,
    PROCESSING,
    STOPPED,
    ERROR,
    TARGET_REACHED
}

/**
 * A dedicated microphone foreground service. It never reuses the Media3
 * recitation service and is started only after an explicit visible-UI action.
 * START_NOT_STICKY prevents automatic microphone restart after process death.
 */
internal class VoiceCountingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var activeRecorder: android.media.AudioRecord? = null
    private var sessionJob: Job? = null
    private var currentStartId: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentStartId = startId
        when (intent?.action) {
            ACTION_STOP -> {
                requestStop()
                if (sessionJob == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(startId)
                }
            }
            ACTION_START -> {
                if (sessionJob?.isActive == true) {
                    publishState(VoiceServiceState.LISTENING, "Voice counting is already active.")
                    return START_NOT_STICKY
                }
                val args = SessionArguments.from(intent)
                if (args == null) {
                    publishState(VoiceServiceState.ERROR, "The selected Dhikr voice profile is invalid.")
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    publishState(VoiceServiceState.ERROR, "Microphone permission is required.")
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                try {
                    startMicrophoneForeground()
                } catch (error: Exception) {
                    Log.w(TAG, "Could not start the microphone foreground service.", error)
                    publishState(VoiceServiceState.ERROR, "Android could not start microphone access. Open the app and try again.")
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                stopRequested.set(false)
                publishState(VoiceServiceState.PREPARING, "Preparing offline voice recognition…")
                sessionJob = serviceScope.launch {
                    runVoiceSession(args)
                }
            }
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startMicrophoneForeground() {
        createNotificationChannel()
        val notification = buildForegroundNotification()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(this, FOREGROUND_NOTIFICATION_ID, notification, type)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Voice Dhikr counting",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while Serene Dhikr is actively listening for repetitions."
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun buildForegroundNotification(): Notification {
        val stopIntent = Intent(this, VoiceCountingService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Voice counting active")
            .setContentText("Listening for your Dhikr. Tap Stop to end microphone access.")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.mipmap.ic_launcher, "Stop", stopPendingIntent)
            .build()
    }

    private suspend fun runVoiceSession(args: SessionArguments) {
        var recognizer: WhisperArabicRecognizer? = null
        var captureJob: Job? = null
        val segments = Channel<FloatArray>(capacity = MAX_PENDING_SEGMENTS)
        var finalState = VoiceServiceState.STOPPED
        var finalMessage = "Voice counting stopped."
        try {
            if (args.sourceHash != voiceConfigurationHash(args.arabic, args.transliteration)) {
                throw IOException("This Dhikr was edited after voice enrollment. Record a new sample in the Dhikr editor.")
            }
            if (args.referenceText.isBlank()) {
                throw IOException("This Dhikr has no valid voice sample. Open its editor and record one.")
            }
            val modelFiles = VoiceModelRepository.modelFiles(this)
            if (!VoiceModelRepository.isInstalled(this)) {
                throw IOException("Offline voice recognition is not installed. Open this Dhikr's editor and complete voice setup first.")
            }
            recognizer = withContext(Dispatchers.Default) {
                WhisperArabicRecognizer.create(modelFiles)
            }
            if (stopRequested.get()) return

            val recorder = VoiceAudioRecorder.createRecorder(this)
            activeRecorder = recorder
            val segmenter = VoiceRepetitionSegmenter()
            captureJob = serviceScope.launch(Dispatchers.IO) {
                var captureError: Throwable? = null
                val frame = ShortArray(FRAME_SAMPLES)
                try {
                    recorder.startRecording()
                    if (recorder.recordingState != android.media.AudioRecord.RECORDSTATE_RECORDING) {
                        throw IOException("Android did not start microphone capture.")
                    }
                    publishState(VoiceServiceState.LISTENING, "Listening on this device. Speak one complete repetition at a time.")
                    while (!stopRequested.get()) {
                        val read = recorder.read(frame, 0, frame.size, android.media.AudioRecord.READ_BLOCKING)
                        when {
                            read == android.media.AudioRecord.ERROR_DEAD_OBJECT ->
                                throw IOException("The microphone was disconnected.")
                            read == android.media.AudioRecord.ERROR_INVALID_OPERATION ||
                                read == android.media.AudioRecord.ERROR_BAD_VALUE ->
                                throw IOException("The microphone could not provide audio.")
                            read > 0 -> {
                                val floatFrame = FloatArray(read) { index -> frame[index] / 32768.0f }
                                val segment = segmenter.acceptFrame(floatFrame)
                                floatFrame.fill(0f)
                                if (segment != null) {
                                    if (!segments.trySend(segment).isSuccess) {
                                        segment.fill(0f)
                                        publishState(
                                            VoiceServiceState.LISTENING,
                                            "Recognition is busy. Pause briefly, then repeat your Dhikr."
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (!stopRequested.get()) captureError = error
                } finally {
                    stopRequested.set(true)
                    segmenter.discardPending()
                    frame.fill(0)
                    runCatching {
                        if (recorder.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                            recorder.stop()
                        }
                    }
                    recorder.release()
                    activeRecorder = null
                    segments.close(captureError)
                }
            }

            for (segment in segments) {
                if (stopRequested.get()) {
                    segment.fill(0f)
                    continue
                }
                publishState(VoiceServiceState.PROCESSING, "Checking the completed phrase locally…")
                val transcript = try {
                    withContext(Dispatchers.Default) { recognizer.transcribe(segment) }
                } finally {
                    segment.fill(0f)
                }
                if (stopRequested.get()) break

                if (!isDhikrPhraseMatch(args.referenceText, transcript)) {
                    publishState(
                        VoiceServiceState.LISTENING,
                        "Not counted — please repeat the configured Dhikr.",
                        transcript.take(MAX_BROADCAST_TRANSCRIPT_CHARS)
                    )
                    continue
                }
                if (stopRequested.get()) break

                val outcome = withContext(Dispatchers.IO) {
                    TasbeehStore(applicationContext).countDhikr(
                        dhikr = args.title,
                        arabic = args.arabic,
                        transliteration = args.transliteration,
                        meaning = args.meaning,
                        fallbackTarget = args.fallbackTarget,
                        now = System.currentTimeMillis()
                    )
                }
                when (outcome) {
                    CountOutcome.PAUSED -> {
                        finalMessage = "Your counter is paused. Resume the session before using voice counting."
                        finalState = VoiceServiceState.STOPPED
                        stopRequested.set(true)
                        break
                    }
                    CountOutcome.TARGET_REACHED -> {
                        TargetReachedFeedback.onTargetReached(applicationContext)
                        finalMessage = "TARGET REACHED"
                        finalState = VoiceServiceState.TARGET_REACHED
                        stopRequested.set(true)
                        break
                    }
                    CountOutcome.COUNTED -> {
                        publishState(
                            VoiceServiceState.LISTENING,
                            "Counted one repetition.",
                            transcript.take(MAX_BROADCAST_TRANSCRIPT_CHARS),
                            counted = true
                        )
                    }
                }
            }
            captureJob.join()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Voice counting session stopped after an error.", error)
            finalState = VoiceServiceState.ERROR
            finalMessage = error.localizedMessage
                ?.takeIf { it.isNotBlank() }
                ?.take(180)
                ?: "Voice counting could not continue. Your saved counter remains available."
        } finally {
            stopRequested.set(true)
            runCatching { activeRecorder?.stop() }
            captureJob?.cancel()
            captureJob?.join()
            segmentsCloseAndClear(segments)
            recognizer?.close()
            publishState(finalState, finalMessage)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(currentStartId)
            sessionJob = null
        }
    }

    private fun segmentsCloseAndClear(channel: Channel<FloatArray>) {
        channel.close()
        while (true) {
            val next = channel.tryReceive().getOrNull() ?: break
            next.fill(0f)
        }
    }

    private fun requestStop() {
        stopRequested.set(true)
        runCatching { activeRecorder?.stop() }
    }

    private fun publishState(
        state: VoiceServiceState,
        message: String,
        transcript: String = "",
        counted: Boolean = false
    ) {
        val intent = Intent(ACTION_STATE).setPackage(packageName).apply {
            putExtra(EXTRA_STATE, state.name)
            putExtra(EXTRA_MESSAGE, message.take(180))
            putExtra(EXTRA_TRANSCRIPT, transcript.take(MAX_BROADCAST_TRANSCRIPT_CHARS))
            putExtra(EXTRA_COUNTED, counted)
        }
        sendBroadcast(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        requestStop()
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        requestStop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private data class SessionArguments(
        val id: String,
        val title: String,
        val arabic: String,
        val transliteration: String,
        val meaning: String,
        val referenceText: String,
        val sourceHash: String,
        val fallbackTarget: Int
    ) {
        companion object {
            fun from(intent: Intent): SessionArguments? {
                val id = intent.getStringExtra(EXTRA_DHIKR_ID)?.takeIf { it.isNotBlank() } ?: return null
                val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: return null
                val arabic = intent.getStringExtra(EXTRA_ARABIC).orEmpty().take(800)
                val transliteration = intent.getStringExtra(EXTRA_TRANSLITERATION).orEmpty().take(800)
                val meaning = intent.getStringExtra(EXTRA_MEANING).orEmpty().take(500)
                val reference = intent.getStringExtra(EXTRA_REFERENCE_TEXT).orEmpty().take(1000)
                val sourceHash = intent.getStringExtra(EXTRA_SOURCE_HASH).orEmpty().take(64)
                val target = intent.getIntExtra(EXTRA_TARGET, 33).coerceIn(1, 9999)
                if (reference.isBlank() || sourceHash.isBlank()) return null
                return SessionArguments(id, title.take(60), arabic, transliteration, meaning, reference, sourceHash, target)
            }
        }
    }

    companion object {
        private const val TAG = "VoiceCountingService"
        private const val ACTION_START = "com.amirmalik.tasbeeh.voice.START"
        private const val ACTION_STOP = "com.amirmalik.tasbeeh.voice.STOP"
        const val ACTION_STATE = "com.amirmalik.tasbeeh.voice.STATE"
        const val EXTRA_STATE = "state"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_TRANSCRIPT = "transcript"
        const val EXTRA_COUNTED = "counted"
        private const val EXTRA_DHIKR_ID = "dhikr_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARABIC = "arabic"
        private const val EXTRA_TRANSLITERATION = "transliteration"
        private const val EXTRA_MEANING = "meaning"
        private const val EXTRA_REFERENCE_TEXT = "reference_text"
        private const val EXTRA_SOURCE_HASH = "source_hash"
        private const val EXTRA_TARGET = "target"
        private const val FOREGROUND_CHANNEL_ID = "voice_counting_foreground_v1"
        private const val FOREGROUND_NOTIFICATION_ID = 6202
        private const val STOP_REQUEST_CODE = 6203
        private const val MAX_PENDING_SEGMENTS = 2
        private const val MAX_BROADCAST_TRANSCRIPT_CHARS = 180
        private const val FRAME_SAMPLES = 320

        fun createStartIntent(context: Context, dhikr: CustomDhikr, target: Int): Intent =
            Intent(context, VoiceCountingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DHIKR_ID, dhikr.id)
                putExtra(EXTRA_TITLE, dhikr.title)
                putExtra(EXTRA_ARABIC, dhikr.arabic)
                putExtra(EXTRA_TRANSLITERATION, dhikr.transliteration)
                putExtra(EXTRA_MEANING, dhikr.meaning)
                putExtra(EXTRA_REFERENCE_TEXT, dhikr.voiceReferenceText)
                putExtra(EXTRA_SOURCE_HASH, dhikr.voiceProfileSourceHash)
                putExtra(EXTRA_TARGET, target.coerceIn(1, 9999))
            }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, VoiceCountingService::class.java).setAction(ACTION_STOP)
                )
            }.onFailure { Log.w(TAG, "Could not send the voice-count stop command.", it) }
        }
    }
}
