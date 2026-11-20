package com.amirmalik.tasbeeh.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.amirmalik.tasbeeh.R
import com.amirmalik.tasbeeh.data.CountOutcome
import java.security.MessageDigest
import java.util.LinkedHashSet
import java.util.Locale

internal const val TARGET_REACHED_ANNOUNCEMENT = "TARGET REACHED"
private const val RESERVED_TARGET_NOTIFICATION_ID = 6201

internal fun shouldAnnounceTargetReached(outcome: CountOutcome): Boolean =
    outcome == CountOutcome.TARGET_REACHED

internal fun shouldAnnounceSession(sessionId: String, announcedSessionIds: Set<String>): Boolean =
    sessionId.isNotBlank() && sessionId !in announcedSessionIds

internal fun updatedAnnouncedSessionIds(
    previous: Set<String>,
    sessionId: String,
    maxEntries: Int = 64
): Set<String> {
    if (sessionId.isBlank() || maxEntries <= 0) return previous
    val updated = LinkedHashSet<String>(previous.size + 1)
    previous.forEach { if (it.isNotBlank()) updated.add(it) }
    updated.add(sessionId)
    while (updated.size > maxEntries) {
        val oldest = updated.iterator().next()
        updated.remove(oldest)
    }
    return updated
}

internal fun notificationIdForSession(sessionId: String): Int {
    val bytes = MessageDigest.getInstance("SHA-256").digest(sessionId.toByteArray(Charsets.UTF_8))
    var value = ((bytes[0].toInt() and 0xff) shl 24) or
        ((bytes[1].toInt() and 0xff) shl 16) or
        ((bytes[2].toInt() and 0xff) shl 8) or
        (bytes[3].toInt() and 0xff)
    value = value and Int.MAX_VALUE
    return when (value) {
        0, RESERVED_TARGET_NOTIFICATION_ID -> value xor 0x13579BDF
        else -> value
    }
}

/**
 * Shared target feedback for tap and voice counts.
 *
 * Session IDs are persisted so duplicate target events across Activity recreation
 * or service/UI observation do not produce another speech announcement. Notification
 * IDs are deterministic per session, providing a second layer of deduplication.
 */
internal object TargetReachedFeedback {
    private const val TAG = "DhikrTargetTts"
    private const val CHANNEL_ID = "dhikr_target_reached_v1"
    private const val PREFS_NAME = "target_completion_feedback_v1"
    private const val KEY_ANNOUNCED_SESSION_IDS = "announced_session_ids"
    private const val MAX_PERSISTED_SESSION_IDS = 64

    private val announcementLock = Any()
    private val inProcessAnnouncedIds = LinkedHashSet<String>()
    @Volatile private var engine: TextToSpeech? = null
    private var initializing = false
    private val pendingUtteranceIds = ArrayDeque<String>()

    fun onTargetReached(context: Context, sessionId: String) {
        if (!markSessionAnnounced(context.applicationContext, sessionId)) return
        announce(context, sessionId)
        showNotification(context, sessionId)
    }

    private fun markSessionAnnounced(context: Context, sessionId: String): Boolean {
        if (sessionId.isBlank()) return false
        synchronized(announcementLock) {
            if (sessionId in inProcessAnnouncedIds) return false
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val previouslyAnnounced = preferences
                .getStringSet(KEY_ANNOUNCED_SESSION_IDS, emptySet())
                ?.toSet()
                .orEmpty()
            if (!shouldAnnounceSession(sessionId, previouslyAnnounced)) {
                inProcessAnnouncedIds.add(sessionId)
                return false
            }

            inProcessAnnouncedIds.add(sessionId)
            while (inProcessAnnouncedIds.size > MAX_PERSISTED_SESSION_IDS) {
                val oldest = inProcessAnnouncedIds.iterator().next()
                inProcessAnnouncedIds.remove(oldest)
            }

            val updated = updatedAnnouncedSessionIds(
                previous = previouslyAnnounced,
                sessionId = sessionId,
                maxEntries = MAX_PERSISTED_SESSION_IDS
            )
            val saved = runCatching {
                preferences.edit()
                    .putStringSet(KEY_ANNOUNCED_SESSION_IDS, updated)
                    .commit()
            }.getOrDefault(false)
            if (!saved) {
                Log.w(TAG, "Could not persist target-alert deduplication for this session.")
            }
            return true
        }
    }

    private fun announce(context: Context, sessionId: String) {
        val applicationContext = context.applicationContext
        synchronized(announcementLock) {
            val existing = engine
            if (existing != null && !initializing) {
                speak(existing, sessionId)
                return
            }
            if (pendingUtteranceIds.size < MAX_PENDING_ANNOUNCEMENTS) {
                pendingUtteranceIds.addLast(sessionId)
            }
            if (initializing) return

            initializing = true
            try {
                engine = TextToSpeech(applicationContext) { status ->
                    val speechEngine = engine
                    synchronized(announcementLock) {
                        initializing = false
                        if (status != TextToSpeech.SUCCESS || speechEngine == null) {
                            pendingUtteranceIds.clear()
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
                            while (pendingUtteranceIds.isNotEmpty()) {
                                speak(speechEngine, pendingUtteranceIds.removeFirst())
                            }
                        }.onFailure { error ->
                            pendingUtteranceIds.clear()
                            Log.w(TAG, "Could not announce the completed Dhikr target.", error)
                        }
                    }
                }
            } catch (error: Exception) {
                initializing = false
                pendingUtteranceIds.clear()
                Log.w(TAG, "Could not create the Android TTS engine.", error)
            }
        }
    }

    private fun speak(speechEngine: TextToSpeech, sessionId: String) {
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        runCatching {
            speechEngine.speak(
                TARGET_REACHED_ANNOUNCEMENT,
                TextToSpeech.QUEUE_ADD,
                params,
                "serene-dhikr-target-$sessionId"
            )
        }.onFailure { error ->
            Log.w(TAG, "Could not speak the target-completion announcement.", error)
        }
    }

    private fun showNotification(context: Context, sessionId: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
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
            manager.notify(notificationIdForSession(sessionId), notification)
        }.onFailure { error ->
            Log.w(TAG, "Could not post the target-completion notification.", error)
        }
    }

    private const val MAX_PENDING_ANNOUNCEMENTS = 3
}
