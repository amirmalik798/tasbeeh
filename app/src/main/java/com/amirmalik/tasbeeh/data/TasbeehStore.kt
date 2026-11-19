package com.amirmalik.tasbeeh.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.tasbeehDataStore by preferencesDataStore(name = "tasbeeh_state")

const val DEFAULT_APP_FONT_SCALE = 1.12f

enum class SessionStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    INCOMPLETE;

    companion object {
        fun fromStoredValue(value: String?): SessionStatus =
            values().firstOrNull { it.name == value } ?: COMPLETED
    }
}

enum class CountOutcome {
    COUNTED,
    PAUSED
}

internal fun visibleCounterValue(storedCount: Int, sessionStatus: SessionStatus?): Int =
    if (sessionStatus == SessionStatus.INCOMPLETE) 0 else storedCount.coerceAtLeast(0)

internal fun canUndoCount(count: Int, sessionStatus: SessionStatus?): Boolean =
    count > 0 &&
        sessionStatus != SessionStatus.PAUSED &&
        sessionStatus != SessionStatus.COMPLETED &&
        sessionStatus != SessionStatus.INCOMPLETE

internal fun incrementCounterValueSafely(storedCount: Int): Int {
    val nonNegativeCount = storedCount.coerceAtLeast(0)
    return if (nonNegativeCount == Int.MAX_VALUE) Int.MAX_VALUE else nonNegativeCount + 1
}

enum class SessionEventType { STARTED, PAUSED, RESUMED, ENDED }

data class SessionEvent(val type: SessionEventType, val at: Long)

internal fun legacySessionEvents(startedAt: Long, endedAt: Long?): List<SessionEvent> =
    buildList {
        if (startedAt > 0L) add(SessionEvent(SessionEventType.STARTED, startedAt))
        endedAt?.takeIf { it > 0L }?.let { add(SessionEvent(SessionEventType.ENDED, it)) }
    }

data class SavedSession(
    val id: String,
    val dhikr: String,
    val count: Int,
    val target: Int,
    val startedAt: Long,
    val arabic: String = "",
    val transliteration: String = "",
    val meaning: String = "",
    val status: SessionStatus = SessionStatus.ACTIVE,
    val endedAt: Long? = null,
    val durationMs: Long? = null,
    val activeSegmentStartedAt: Long? = startedAt.takeIf { it > 0L },
    val events: List<SessionEvent> = listOf(SessionEvent(SessionEventType.STARTED, startedAt)).filter { it.at > 0L }
) {
    fun durationAt(now: Long): Long? {
        if (startedAt <= 0L) return null
        val saved = durationMs ?: accumulatedActiveDuration(this)
        return if (status == SessionStatus.ACTIVE && endedAt == null && activeSegmentStartedAt != null) {
            saved + (now - activeSegmentStartedAt).coerceAtLeast(0L)
        } else saved
    }
}

internal fun accumulatedActiveDuration(session: SavedSession): Long =
    session.durationMs ?: session.events.zipWithNext().sumOf { (a, b) ->
        if (a.type == SessionEventType.STARTED || a.type == SessionEventType.RESUMED) {
            (b.at - a.at).coerceAtLeast(0L)
        } else 0L
    }.let { calculated ->
        if (session.events.isEmpty() && session.startedAt > 0L && session.endedAt != null)
            (session.endedAt - session.startedAt).coerceAtLeast(0L) else calculated
    }

internal fun finishSession(
    session: SavedSession,
    now: Long,
    status: SessionStatus
): SavedSession {
    require(status == SessionStatus.COMPLETED || status == SessionStatus.INCOMPLETE)
    return session.copy(
        status = status,
        endedAt = now,
        durationMs = accumulatedActiveDuration(session) +
            if (session.status == SessionStatus.ACTIVE && session.activeSegmentStartedAt != null)
                (now - session.activeSegmentStartedAt).coerceAtLeast(0L) else 0L,
        activeSegmentStartedAt = null,
        events = session.events + SessionEvent(SessionEventType.ENDED, now)
    )
}

data class CustomDhikr(
    val id: String,
    val title: String,
    val arabic: String = "",
    val transliteration: String = "",
    val meaning: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

internal fun sessionSnapshotForDisplay(session: SavedSession?, counterValue: Int): SavedSession? =
    session?.takeIf {
        it.status == SessionStatus.ACTIVE ||
            it.status == SessionStatus.PAUSED ||
            (it.status == SessionStatus.COMPLETED && counterValue == it.count)
    }

data class TasbeehState(
    val count: Int = 0,
    val target: Int = 33,
    val dhikr: String = "SubhanAllah",
    val soundEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val ttsVoicePreference: String = "female",
    val fontScale: Float = DEFAULT_APP_FONT_SCALE,
    val currentSession: SavedSession? = null,
    val customDhikrs: List<CustomDhikr> = emptyList(),
    val selectedCustomDhikrId: String? = null
)

class TasbeehStore(private val context: Context) {
    private object Keys {
        val count = intPreferencesKey("count")
        val target = intPreferencesKey("target")
        val dhikr = stringPreferencesKey("dhikr")
        val sound = booleanPreferencesKey("sound")
        val haptics = booleanPreferencesKey("haptics")
        val ttsVoicePreference = stringPreferencesKey("tts_voice_preference")
        val fontScale = floatPreferencesKey("font_scale")
        val currentSession = stringPreferencesKey("current_session")

        // Legacy keys are read once during migration, then removed.
        val legacySessions = stringPreferencesKey("sessions")
        val legacyOpenSession = stringPreferencesKey("open_session")
        val legacyLastCompletedCount = intPreferencesKey("last_completed_count")
        val legacyLastCompletedDhikr = stringPreferencesKey("last_completed_dhikr")

        val customDhikrs = stringPreferencesKey("custom_dhikrs")
        val selectedCustomDhikrId = stringPreferencesKey("selected_custom_dhikr_id")
    }

    val state: Flow<TasbeehState> = context.tasbeehDataStore.data.map { prefs ->
        val currentSession = decodeSession(prefs[Keys.currentSession].orEmpty())
        TasbeehState(
            count = visibleCounterValue(prefs[Keys.count] ?: 0, currentSession?.status),
            target = (prefs[Keys.target] ?: 33).coerceIn(1, 9999),
            dhikr = prefs[Keys.dhikr] ?: "SubhanAllah",
            soundEnabled = prefs[Keys.sound] ?: false,
            hapticsEnabled = prefs[Keys.haptics] ?: true,
            ttsVoicePreference = prefs[Keys.ttsVoicePreference]?.takeIf { it == "male" || it == "female" } ?: "female",
            fontScale = (prefs[Keys.fontScale] ?: DEFAULT_APP_FONT_SCALE).coerceIn(1.0f, 1.25f),
            currentSession = currentSession,
            customDhikrs = decodeCustomDhikrs(prefs[Keys.customDhikrs].orEmpty()),
            selectedCustomDhikrId = prefs[Keys.selectedCustomDhikrId]
        )
    }

    /**
     * Migrates only the current/open session. Older historical sessions are not retained.
     */
    suspend fun migrateToSingleSession() {
        context.tasbeehDataStore.edit { prefs ->
            if (decodeSession(prefs[Keys.currentSession].orEmpty()) == null) {
                // Recover from a missing or malformed new-format record before
                // clearing legacy keys, so a partial/corrupt value cannot mask
                // a still-readable session from the previous app version.
                prefs.remove(Keys.currentSession)
                val legacyOpen = decodeLegacySession(prefs[Keys.legacyOpenSession].orEmpty())
                val latestLegacy = decodeLegacySessions(prefs[Keys.legacySessions].orEmpty()).firstOrNull()
                val recovered = legacyOpen ?: latestLegacy ?: run {
                    val legacyCount = (prefs[Keys.count] ?: 0).coerceAtLeast(0)
                    if (legacyCount > 0) {
                        // The former counter did not always create a session record.
                        // Keep its count, but do not invent a historical start time.
                        SavedSession(
                            id = UUID.randomUUID().toString(),
                            dhikr = prefs[Keys.dhikr] ?: "SubhanAllah",
                            count = legacyCount,
                            target = (prefs[Keys.target] ?: 33).coerceIn(1, 9999),
                            startedAt = 0L,
                            status = SessionStatus.ACTIVE
                        )
                    } else null
                }

                recovered?.let {
                    prefs[Keys.currentSession] = encodeSession(it)
                }
            }

            prefs.remove(Keys.legacySessions)
            prefs.remove(Keys.legacyOpenSession)
            prefs.remove(Keys.legacyLastCompletedCount)
            prefs.remove(Keys.legacyLastCompletedDhikr)
        }
    }

    suspend fun update(
        count: Int? = null,
        target: Int? = null,
        dhikr: String? = null,
        soundEnabled: Boolean? = null,
        hapticsEnabled: Boolean? = null,
        fontScale: Float? = null,
        ttsVoicePreference: String? = null
    ) {
        context.tasbeehDataStore.edit { prefs ->
            count?.let { prefs[Keys.count] = it.coerceAtLeast(0) }
            target?.let {
                val nextTarget = it.coerceIn(1, 9999)
                val current = decodeSession(prefs[Keys.currentSession].orEmpty())
                val sessionIsOpen = current?.status == SessionStatus.ACTIVE ||
                    current?.status == SessionStatus.PAUSED
                if (!sessionIsOpen && prefs[Keys.target] != nextTarget && (prefs[Keys.count] ?: 0) > 0) {
                    prefs[Keys.count] = 0
                }
                prefs[Keys.target] = nextTarget
            }
            dhikr?.let { prefs[Keys.dhikr] = it }
            soundEnabled?.let { prefs[Keys.sound] = it }
            hapticsEnabled?.let { prefs[Keys.haptics] = it }
            ttsVoicePreference?.let { if (it == "male" || it == "female") prefs[Keys.ttsVoicePreference] = it }
            fontScale?.let { prefs[Keys.fontScale] = it.coerceIn(1.0f, 1.25f) }
        }
    }

    /**
     * The count and its current-session snapshot are saved atomically. The
     * session history is updated by session lifecycle events, not each tap.
     */
    suspend fun countDhikr(
        dhikr: String,
        arabic: String,
        transliteration: String,
        meaning: String,
        fallbackTarget: Int,
        now: Long
    ): CountOutcome {
        var outcome = CountOutcome.COUNTED
        context.tasbeehDataStore.edit { prefs ->
            val existing = decodeSession(prefs[Keys.currentSession].orEmpty())
            if (existing?.status == SessionStatus.PAUSED) {
                outcome = CountOutcome.PAUSED
                return@edit
            }

            val target = if (existing?.status == SessionStatus.ACTIVE) {
                existing.target
            } else {
                (prefs[Keys.target] ?: fallbackTarget).coerceIn(1, 9999)
            }
            val storedCount = (prefs[Keys.count] ?: 0).coerceAtLeast(0)
            val startsNewSession = existing == null ||
                existing.status == SessionStatus.COMPLETED ||
                existing.status == SessionStatus.INCOMPLETE
            val baseCount = if (startsNewSession) 0 else storedCount
            val nextCount = incrementCounterValueSafely(baseCount)

            val session = if (startsNewSession) {
                SavedSession(
                    id = UUID.randomUUID().toString(),
                    dhikr = dhikr,
                    count = nextCount,
                    target = target,
                    startedAt = now,
                    arabic = arabic,
                    transliteration = transliteration,
                    meaning = meaning,
                    status = SessionStatus.ACTIVE
                )
            } else {
                existing!!.copy(count = nextCount)
            }

            prefs[Keys.count] = nextCount
            val saved = if (nextCount >= target) {
                finishSession(session, now, SessionStatus.COMPLETED)
            } else {
                session.copy(status = SessionStatus.ACTIVE, endedAt = null)
            }
            prefs[Keys.currentSession] = encodeSession(saved)
        }
        return outcome
    }

    suspend fun undoCount(now: Long) {
        context.tasbeehDataStore.edit { prefs ->
            val currentCount = (prefs[Keys.count] ?: 0).coerceAtLeast(0)
            val session = decodeSession(prefs[Keys.currentSession].orEmpty())
            if (!canUndoCount(currentCount, session?.status)) return@edit

            val nextCount = (currentCount - 1).coerceAtLeast(0)
            prefs[Keys.count] = nextCount
            if (session != null) {
                if (nextCount == 0) {
                    prefs.remove(Keys.currentSession)
                } else {
                    val updated = session.copy(count = nextCount, status = SessionStatus.ACTIVE, endedAt = null, durationMs = null)
                    prefs[Keys.currentSession] = encodeSession(updated)
                }
            }
        }
    }

    suspend fun pauseCurrentSession() {
        context.tasbeehDataStore.edit { prefs ->
            val session = decodeSession(prefs[Keys.currentSession].orEmpty()) ?: return@edit
            if (session.status != SessionStatus.ACTIVE || session.count <= 0) return@edit
            val at = System.currentTimeMillis()
            val paused = session.copy(
                status = SessionStatus.PAUSED,
                durationMs = accumulatedActiveDuration(session) +
                    (session.activeSegmentStartedAt?.let { (at - it).coerceAtLeast(0L) } ?: 0L),
                activeSegmentStartedAt = null,
                events = session.events + SessionEvent(SessionEventType.PAUSED, at)
            )
            prefs[Keys.currentSession] = encodeSession(paused)
        }
    }

    suspend fun resumeCurrentSession() {
        context.tasbeehDataStore.edit { prefs ->
            val session = decodeSession(prefs[Keys.currentSession].orEmpty()) ?: return@edit
            if (session.status != SessionStatus.PAUSED) return@edit
            val at = System.currentTimeMillis()
            val resumed = session.copy(
                status = SessionStatus.ACTIVE,
                durationMs = accumulatedActiveDuration(session),
                activeSegmentStartedAt = at,
                events = session.events + SessionEvent(SessionEventType.RESUMED, at)
            )
            prefs[Keys.currentSession] = encodeSession(resumed)
        }
    }

    suspend fun finishCurrentSession(now: Long, resetCounter: Boolean = true) {
        context.tasbeehDataStore.edit { prefs ->
            val session = decodeSession(prefs[Keys.currentSession].orEmpty())
            if (session != null &&
                (session.status == SessionStatus.ACTIVE || session.status == SessionStatus.PAUSED)
            ) {
                val finished = finishSession(session, now, SessionStatus.INCOMPLETE)
                prefs[Keys.currentSession] = encodeSession(finished)
            }
            if (resetCounter) prefs[Keys.count] = 0
        }
    }

    suspend fun resetCounter(saveSession: Boolean, now: Long) {
        context.tasbeehDataStore.edit { prefs ->
            val session = decodeSession(prefs[Keys.currentSession].orEmpty())
            val isOpen = session?.status == SessionStatus.ACTIVE || session?.status == SessionStatus.PAUSED
            when {
                saveSession && isOpen -> {
                    val finished = finishSession(session!!, now, SessionStatus.INCOMPLETE)
                    prefs[Keys.currentSession] = encodeSession(finished)
                }
                !saveSession && isOpen -> {
                    prefs.remove(Keys.currentSession)
                }
            }
            prefs[Keys.count] = 0
        }
    }

    suspend fun selectBuiltInDhikr(
        value: String,
        arabic: String = "",
        now: Long = System.currentTimeMillis()
    ) {
        if (value.isBlank()) return
        context.tasbeehDataStore.edit { prefs ->
            finishOpenSessionIfNeeded(prefs, now)
            prefs[Keys.count] = 0
            prefs[Keys.dhikr] = value
            prefs.remove(Keys.selectedCustomDhikrId)
        }
    }

    suspend fun selectCustomDhikr(dhikr: CustomDhikr, now: Long = System.currentTimeMillis()) {
        if (dhikr.id.isBlank()) return
        context.tasbeehDataStore.edit { prefs ->
            val selected = decodeCustomDhikrs(prefs[Keys.customDhikrs].orEmpty())
                .firstOrNull { it.id == dhikr.id } ?: return@edit
            finishOpenSessionIfNeeded(prefs, now)
            prefs[Keys.count] = 0
            prefs[Keys.dhikr] = selected.title
            prefs[Keys.selectedCustomDhikrId] = selected.id
        }
    }

    suspend fun saveCustomDhikr(dhikr: CustomDhikr) {
        val cleaned = dhikr.copy(
            title = dhikr.title.trim().take(60),
            arabic = dhikr.arabic.trim().take(800),
            transliteration = dhikr.transliteration.trim().take(800),
            meaning = dhikr.meaning.trim().take(500)
        )
        if (cleaned.id.isBlank() || cleaned.title.isBlank()) return

        context.tasbeehDataStore.edit { prefs ->
            val current = decodeCustomDhikrs(prefs[Keys.customDhikrs].orEmpty()).toMutableList()
            val existingIndex = current.indexOfFirst { it.id == cleaned.id }
            if (existingIndex >= 0) current[existingIndex] = cleaned else current.add(0, cleaned)
            prefs[Keys.customDhikrs] = encodeCustomDhikrs(current)
            if (prefs[Keys.selectedCustomDhikrId] == cleaned.id) {
                prefs[Keys.dhikr] = cleaned.title
            }
        }
    }

    suspend fun deleteCustomDhikr(id: String, now: Long = System.currentTimeMillis()) {
        if (id.isBlank()) return
        context.tasbeehDataStore.edit { prefs ->
            val remaining = decodeCustomDhikrs(prefs[Keys.customDhikrs].orEmpty())
                .filterNot { it.id == id }
            prefs[Keys.customDhikrs] = encodeCustomDhikrs(remaining)
            if (prefs[Keys.selectedCustomDhikrId] == id) {
                finishOpenSessionIfNeeded(prefs, now)
                prefs[Keys.count] = 0
                prefs[Keys.dhikr] = "SubhanAllah"
                prefs.remove(Keys.selectedCustomDhikrId)
            }
        }
    }

    private fun finishOpenSessionIfNeeded(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        now: Long
    ) {
        val session = decodeSession(prefs[Keys.currentSession].orEmpty()) ?: return
        if (session.status == SessionStatus.ACTIVE || session.status == SessionStatus.PAUSED) {
            val finished = finishSession(session, now, SessionStatus.INCOMPLETE)
            prefs[Keys.currentSession] = encodeSession(finished)
        }
    }

    private fun decodeLegacySessions(raw: String): List<SavedSession> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                decodeLegacySession(item)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun decodeLegacySession(raw: String): SavedSession? = runCatching {
        if (raw.isBlank()) null else decodeLegacySession(JSONObject(raw))
    }.getOrNull()

    private fun decodeLegacySession(item: JSONObject): SavedSession? {
        val dhikr = item.optString("dhikr", "").trim()
        if (dhikr.isEmpty()) return null
        val startedAt = item.optLong("startedAt").takeIf { it > 0L } ?: 0L
        val endedAt = item.optLong("endedAt").takeIf { it > 0L }
            ?: item.optLong("completedAt").takeIf { it > 0L }
        val storedStatus = SessionStatus.fromStoredValue(item.optString("status", "COMPLETED"))
        val status = if (storedStatus == SessionStatus.ACTIVE || storedStatus == SessionStatus.PAUSED) {
            storedStatus
        } else if (storedStatus == SessionStatus.INCOMPLETE) {
            SessionStatus.INCOMPLETE
        } else {
            SessionStatus.COMPLETED
        }
        val duration = if (startedAt > 0L && endedAt != null) {
            (endedAt - startedAt).coerceAtLeast(0L)
        } else null
        return SavedSession(
            id = item.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            dhikr = dhikr,
            count = item.optInt("count").coerceAtLeast(0),
            target = item.optInt("target", 33).coerceIn(1, 9999),
            startedAt = startedAt,
            arabic = item.optString("arabic", ""),
            transliteration = item.optString("transliteration", ""),
            meaning = item.optString("meaning", ""),
            status = status,
            endedAt = endedAt,
            durationMs = duration,
            events = legacySessionEvents(startedAt, endedAt)
        )
    }

    private fun decodeSession(raw: String): SavedSession? = runCatching {
        if (raw.isBlank()) null else {
            val item = JSONObject(raw)
            val dhikr = item.optString("dhikr", "").trim()
            if (dhikr.isEmpty()) null else {
                val startedAt = item.optLong("startedAt").takeIf { it > 0L } ?: 0L
                val endedAt = item.optLong("endedAt").takeIf { it > 0L }
                val duration = if (item.has("durationMs") && !item.isNull("durationMs")) {
                    item.optLong("durationMs").coerceAtLeast(0L)
                } else null
                SavedSession(
                    id = item.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                    dhikr = dhikr,
                    count = item.optInt("count").coerceAtLeast(0),
                    target = item.optInt("target", 33).coerceIn(1, 9999),
                    startedAt = startedAt,
                    arabic = item.optString("arabic", ""),
                    transliteration = item.optString("transliteration", ""),
                    meaning = item.optString("meaning", ""),
                    status = SessionStatus.fromStoredValue(item.optString("status", "ACTIVE")),
                    endedAt = endedAt,
                    durationMs = duration,
                    activeSegmentStartedAt = item.optLong("activeSegmentStartedAt").takeIf { it > 0L }
                        ?: if (item.optString("status") == SessionStatus.ACTIVE.name && endedAt == null) startedAt.takeIf { it > 0L } else null,
                    events = decodeEvents(item.optJSONArray("events")).ifEmpty {
                        buildList {
                            if (startedAt > 0L) add(SessionEvent(SessionEventType.STARTED, startedAt))
                            if (endedAt != null) add(SessionEvent(SessionEventType.ENDED, endedAt))
                        }
                    }
                )
            }
        }
    }.getOrNull()

    private fun encodeSession(session: SavedSession): String = JSONObject().apply {
        put("id", session.id)
        put("dhikr", session.dhikr)
        put("count", session.count)
        put("target", session.target)
        put("startedAt", session.startedAt)
        put("arabic", session.arabic)
        put("transliteration", session.transliteration)
        put("meaning", session.meaning)
        put("status", session.status.name)
        put("endedAt", session.endedAt ?: JSONObject.NULL)
        put("durationMs", session.durationMs ?: JSONObject.NULL)
        put("activeSegmentStartedAt", session.activeSegmentStartedAt ?: JSONObject.NULL)
        put("events", JSONArray().apply {
            session.events.forEach { event ->
                put(JSONObject().put("type", event.type.name).put("at", event.at))
            }
        })
    }.toString()

    private fun decodeEvents(array: JSONArray?): List<SessionEvent> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val type = runCatching { SessionEventType.valueOf(item.optString("type")) }.getOrNull() ?: continue
                val at = item.optLong("at")
                if (at > 0L) add(SessionEvent(type, at))
            }
        }
    }

    private fun decodeCustomDhikrs(raw: String): List<CustomDhikr> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                if (id.isNotEmpty() && title.isNotEmpty()) {
                    add(
                        CustomDhikr(
                            id = id,
                            title = title.take(60),
                            arabic = item.optString("arabic").take(800),
                            transliteration = item.optString("transliteration").take(800),
                            meaning = item.optString("meaning").take(500),
                            createdAt = item.optLong("createdAt")
                        )
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeCustomDhikrs(dhikrs: List<CustomDhikr>): String {
        val array = JSONArray()
        dhikrs.forEach { dhikr ->
            array.put(
                JSONObject().apply {
                    put("id", dhikr.id)
                    put("title", dhikr.title)
                    put("arabic", dhikr.arabic)
                    put("transliteration", dhikr.transliteration)
                    put("meaning", dhikr.meaning)
                    put("createdAt", dhikr.createdAt)
                }
            )
        }
        return array.toString()
    }
}
