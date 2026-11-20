package com.amirmalik.tasbeeh.voice

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.amirmalik.tasbeeh.data.CountOutcome
import androidx.core.app.NotificationManagerCompat
import com.amirmalik.tasbeeh.R
import java.util.Locale

internal fun shouldAnnounceTargetReached(outcome: CountOutcome): Boolean =
    outcome == CountOutcome.TARGET_REACHED

/**
 * Announces the target transition through Android's installed TTS engine.
 * The application context is retained so the service and Activity share one engine.
 */
internal object TargetReachedFeedback {
    private const val TAG = "DhikrTargetTts"
    private const val CHANNEL_ID = "dhikr_target_reached_v1"
    private const val NOTIFICATION_ID = 6201
    private val lock = Any()
    @Volatile private var engine: TextToSpeech? = null
    private var initializing = false
    private var pendingAnnouncements = 0

    fun announce(context: Context) {
        val applicationContext = context.applicationContext
        synchronized(lock) {
            val existing = engine
            if (existing != null && !initializing) {
                speak(existing)
                return
            }
            pendingAnnouncements = (pendingAnnouncements + 1).coerceAtMost(3)
            if (initializing) return
            initializing = true
            engine = TextToSpeech(applicationContext) { status ->
                val speechEngine = engine
                synchronized(lock) {
                    initializing = false
                    if (status != TextToSpeech.SUCCESS || speechEngine == null) {
                        pendingAnnouncements = 0
                        Log.w(TAG, "The device TTS engine could not be initialized.")
                        return@synchronized
                    }
                    runCatching {
                        speechEngine.setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        val languageResult = speechEngine.setLanguage(Locale.US)
                        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            speechEngine.setLanguage(Locale.getDefault())
                        }
                        speechEngine.setSpeechRate(0.96f)
                        val queued = pendingAnnouncements
                        pendingAnnouncements = 0
                        repeat(queued) { speak(speechEngine) }
                    }.onFailure { error ->
                        pendingAnnouncements = 0
                        Log.w(TAG, "Could not announce the completed Dhikr target.", error)
                    }
                }
            }
        }
    }

    private fun speak(speechEngine: TextToSpeech) {
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        speechEngine.speak(
            "TARGET REACHED",
            TextToSpeech.QUEUE_ADD,
            params,
            "serene-dhikr-target-reached"
        )
    }

    fun showNotification(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as? android.app.NotificationManager ?: return
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Dhikr target reached",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A quiet notice when your Dhikr target is completed."
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Target reached")
            .setContentText("MashaAllah — your Dhikr target is complete.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "Could not post the target-completion notification.", it) }
    }

    fun onTargetReached(context: Context) {
        announce(context)
        showNotification(context)
    }
}
