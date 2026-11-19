package com.amirmalik.tasbeeh.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.min

private val Context.tasbeehDataStore by preferencesDataStore(name = "tasbeeh_state")

const val SESSION_IDLE_TIMEOUT_MS = 15L * 60L * 1000L
const val DEFAULT_APP_FONT_SCALE = 1.08f

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

data class SavedSession(
    val id: String,
    val dhikr: String,
    val count: Int,
    val target: Int,
    val startedAt: Long,
    val lastActivityAt: Long,
    val arabic: String = "",
    val transliteration: String = "",
    val meaning: String = "",
    val activeDurationMs: Long? = 0L,
    val pausedDurationMs: Long = 0L,
    val pausedAt: Long? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val completedAt: Long? = null,
    val endedAt: Long? = null
) {
    fun activeDurationAt(now: Long): Long {
        val stored = (activeDurationMs ?: 0L).coerceAtLeast(0L)
        if (status != SessionStatus.ACTIVE) return stored
        val currentSegment = (now - lastActivityAt).coerceAtLeast(0L)
            .coerceAtMost(SESSION_IDLE_TIMEOUT_MS)
        return stored + currentSegment
    }

    fun totalElapsedAt(now: Long): Long {
        if (startedAt <= 0L) return 0L
        val end = completedAt ?: endedAt ?: now
        return (end - startedAt).coerceAtLeast(0L)
    }
}

data class CustomDhikr(
    val id: String,
    val title: String,
    val arabic: String = "",
    val transliteration: String = "",
    val meaning: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class TasbeehState(
    val count: Int = 0,
    val target: Int = 33,
    val dhikr: String = "SubhanAllah",
    val soundEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val fontScale: Float = DEFAULT_APP_FONT_SCALE,
    val sessions: List<SavedSession> = emptyList(),
    val openSession: SavedSession? = null,
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
        val fontScale = floatPreferencesKey("font_scale")
        val sessions = stringPreferencesKey("sessions")
        val openSession = stringPreferencesKey("open_session")
        val customDhikrs = stringPreferencesKey("custom_dhikrs")
        val selectedCustomDhikrId = stringPreferencesKey("selected_custom_dhikr_id")
    }

    val state: Flow<TasbeehState> = context.tasbeehDataStore.data.map { prefs ->
        TasbeehState(
            count = (prefs[Keys.count] ?: 0).coerceAtLeast(0),
            target = (prefs[Keys.target] ?: 33).coerceIn(1, 9999),
            dhikr = prefs[Keys.dhikr] ?: "SubhanAllah",
            soundEnabled = prefs[Keys.sound] ?: false,
            hapticsEnabled = prefs[Keys.haptics] ?: true,
            fontScale = (prefs[Keys.fontScale] ?: DEFAULT_APP_FONT_SCALE).coerceIn(1.0f, 1.25f),
            sessions = decodeSessions(prefs[Keys.sessions].orEmpty()),
            openSession = decodeOpenSession(prefs[Keys.openSession].orEmpty()),
            customDhikrs = decodeCustomDhikrs(prefs[Keys.customDhikrs].orEmpty()),
            selectedCustomDhikrId = prefs[Keys.selectedCustomDhikrId]
        )
    }

    suspend fun update(
        count: Int? = null,
        target: Int? = null,
        dhikr: String? = null,
        soundEnabled: Boolean? = null,
        hapticsEnabled: Boolean? = null,
        fontScale: Float? = null
    ) {
        context.tasbeehDataStore.edit { prefs ->
            count?.let { prefs[Keys.count] = it.coerceAtLeast(0) }
            target?.let {
                val nextTarget = it.coerceIn(1, 9999)
                val hasOpenSession = decodeOpenSession(prefs[Keys.openSession].orEmpty()) != null
                if (!hasOpenSession && prefs[Keys.target] != nextTarget && (prefs[Keys.count] ?: 0) > 0) {
                    prefs[Keys.count] = 0
                }
                prefs[Keys.target] = nextTarget
            }
            dhikr?.let { prefs[Keys.dhikr] = it }
            soundEnabled?.let { prefs[Keys.sound] = it }
            hapticsEnabled?.let { prefs[Keys.haptics] = it }
            fontScale?.let { prefs[Keys.fontScale] = it.coerceIn(1.0f, 1.25f) }
        }
    }

    /**
     * Atomically checkpoints the counter and its open session. DataStore's edit
     * transaction prevents rapid taps from overwriting each other with stale UI state.
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
            var open = decodeOpenSession(prefs[Keys.openSession].orEmpty())
            if (open?.status == SessionStatus.PAUSED) {
                outcome = CountOutcome.PAUSED
                return@edit
            }

            if (open?.status == SessionStatus.ACTIVE &&
                now - open.lastActivityAt >= SESSION_IDLE_TIMEOUT_MS
            ) {
                val pauseAt = open.lastActivityAt + SESSION_IDLE_TIMEOUT_MS
                open = open.copy(
                    activeDurationMs = (open.activeDurationMs ?: 0L) + SESSION_IDLE_TIMEOUT_MS,
                    status = SessionStatus.PAUSED,
                    pausedAt = pauseAt
                )
                prefs[Keys.openSession] = encodeSession(open)
                outcome = CountOutcome.PAUSED
                return@edit
            }

            val target = open?.target ?: (prefs[Keys.target] ?: fallbackTarget).coerceIn(1, 9999)
            val storedCount = (prefs[Keys.count] ?: 0).coerceAtLeast(0)
            val latestSaved = decodeSessions(prefs[Keys.sessions].orEmpty()).firstOrNull()
            val previousRoundCompleted = latestSaved?.status == SessionStatus.COMPLETED &&
                latestSaved.count == storedCount
            // A completed round always starts its next round at 1, even if the
            // user changed the default target while the previous round was open.
            val baseCount = if (open == null && (storedCount >= target || previousRoundCompleted)) 0 else storedCount
            val nextCount = baseCount + 1

            val updated = if (open == null) {
                SavedSession(
                    id = UUID.randomUUID().toString(),
                    dhikr = dhikr,
                    count = nextCount,
                    target = target,
                    startedAt = now,
                    lastActivityAt = now,
                    arabic = arabic,
                    transliteration = transliteration,
                    meaning = meaning,
                    activeDurationMs = 0L,
                    status = SessionStatus.ACTIVE
                )
            } else {
                val interval = (now - open.lastActivityAt).coerceAtLeast(0L)
                    .coerceAtMost(SESSION_IDLE_TIMEOUT_MS)
                open.copy(
                    count = nextCount,
                    lastActivityAt = now,
                    activeDurationMs = (open.activeDurationMs ?: 0L) + interval
                )
            }

            prefs[Keys.count] = nextCount
            if (nextCount >= target) {
                val completed = updated.copy(
                    status = SessionStatus.COMPLETED,
                    completedAt = now,
                    endedAt = now,
                    pausedAt = null
                )
                prefs[Keys.sessions] = appendSession(prefs[Keys.sessions].orEmpty(), completed)
                prefs.remove(Keys.openSession)
            } else {
                prefs[Keys.openSession] = encodeSession(updated)
            }
        }
        return outcome
    }

    suspend fun undoCount(now: Long) {
        context.tasbeehDataStore.edit { prefs ->
            val currentCount = (prefs[Keys.count] ?: 0).coerceAtLeast(0)
            if (currentCount == 0) return@edit

            val open = decodeOpenSession(prefs[Keys.openSession].orEmpty())
            if (open?.status == SessionStatus.PAUSED) return@edit
            if (open == null) {
                val latestSaved = decodeSessions(prefs[Keys.sessions].orEmpty()).firstOrNull()
                if (latestSaved?.status == SessionStatus.COMPLETED && latestSaved.count == currentCount) {
                    return@edit
                }
            }
            if (open?.status == SessionStatus.ACTIVE &&
                now - open.lastActivityAt >= SESSION_IDLE_TIMEOUT_MS
            ) {
                val pauseAt = open.lastActivityAt + SESSION_IDLE_TIMEOUT_MS
                prefs[Keys.openSession] = encodeSession(
                    open.copy(
                        activeDurationMs = (open.activeDurationMs ?: 0L) + SESSION_IDLE_TIMEOUT_MS,
                        status = SessionStatus.PAUSED,
                        pausedAt = pauseAt
                    )
                )
                return@edit
            }

            val nextCount = (currentCount - 1).coerceAtLeast(0)
            prefs[Keys.count] = nextCount
            if (open != null) {
                if (nextCount == 0) {
                    prefs.remove(Keys.openSession)
                } else {
                    val interval = (now - open.lastActivityAt).coerceAtLeast(0L)
                        .coerceAtMost(SESSION_IDLE_TIMEOUT_MS)
                    prefs[Keys.openSession] = encodeSession(
                        open.copy(
                            count = nextCount,
                            lastActivityAt = now,
                            activeDurationMs = (open.activeDurationMs ?: 0L) + interval
                        )
                    )
                }
            }
        }
    }

    suspend fun pauseOpenSessionIfIdle(now: Long) {
        context.tasbeehDataStore.edit { prefs ->
            val open = decodeOpenSession(prefs[Keys.openSession].orEmpty()) ?: return@edit
            if (open.status != SessionStatus.ACTIVE) return@edit
            if (now - open.lastActivityAt < SESSION_IDLE_TIMEOUT_MS) return@edit

            val pauseAt = open.lastActivityAt + SESSION_IDLE_TIMEOUT_MS
            prefs[Keys.openSession] = encodeSession(
                open.copy(
                    activeDurationMs = (open.activeDurationMs ?: 0L) + SESSION_IDLE_TIMEOUT_MS,
                    status = SessionStatus.PAUSED,
                    pausedAt = pauseAt
                )
            )
        }
    }

    suspend fun resumeOpenSession(now: Long) {
        context.tasbeehDataStore.edit { prefs ->
            val open = decodeOpenSession(prefs[Keys.openSession].orEmpty()) ?: return@edit
            if (open.status != SessionStatus.PAUSED) return@edit

            val pausedStart = open.pausedAt ?: open.lastActivityAt + SESSION_IDLE_TIMEOUT_MS
            val newlyPaused = (now - pausedStart).coerceAtLeast(0L)
            prefs[Keys.openSession] = encodeSession(
                open.copy(
                    status = SessionStatus.ACTIVE,
                    lastActivityAt = now,
                    pausedAt = null,
                    pausedDurationMs = open.pausedDurationMs + newlyPaused
                )
            )
        }
    }

    suspend fun finishOpenSession(now: Long, resetCounter: Boolean = true) {
        context.tasbeehDataStore.edit { prefs ->
            val open = decodeOpenSession(prefs[Keys.openSession].orEmpty())
            if (open != null && open.count > 0) {
                val finished = finalizeOpenSession(open, SessionStatus.INCOMPLETE, now)
                prefs[Keys.sessions] = appendSession(prefs[Keys.sessions].orEmpty(), finished)
            }
            prefs.remove(Keys.openSession)
            if (resetCounter) prefs[Keys.count] = 0
        }
    }

    suspend fun resetCounter(saveSession: Boolean, now: Long) {
        context.tasbeehDataStore.edit { prefs ->
            val open = decodeOpenSession(prefs[Keys.openSession].orEmpty())
            if (saveSession && open != null && open.count > 0) {
                prefs[Keys.sessions] = appendSession(
                    prefs[Keys.sessions].orEmpty(),
                    finalizeOpenSession(open, SessionStatus.INCOMPLETE, now)
                )
            }
            prefs.remove(Keys.openSession)
            prefs[Keys.count] = 0
        }
    }

    suspend fun selectBuiltInDhikr(value: String, arabic: String = "", now: Long = System.currentTimeMillis()) {
        if (value.isBlank()) return
        context.tasbeehDataStore.edit { prefs ->
            archiveOpenSession(prefs, now)
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
            archiveOpenSession(prefs, now)
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
                archiveOpenSession(prefs, now)
                prefs[Keys.count] = 0
                prefs[Keys.dhikr] = "SubhanAllah"
                prefs.remove(Keys.selectedCustomDhikrId)
            }
        }
    }

    private fun archiveOpenSession(prefs: androidx.datastore.preferences.core.MutablePreferences, now: Long) {
        val open = decodeOpenSession(prefs[Keys.openSession].orEmpty()) ?: return
        if (open.count > 0) {
            prefs[Keys.sessions] = appendSession(
                prefs[Keys.sessions].orEmpty(),
                finalizeOpenSession(open, SessionStatus.INCOMPLETE, now)
            )
        }
        prefs.remove(Keys.openSession)
    }

    private fun finalizeOpenSession(
        session: SavedSession,
        status: SessionStatus,
        now: Long
    ): SavedSession {
        var active = session.activeDurationMs ?: 0L
        var paused = session.pausedDurationMs
        if (session.status == SessionStatus.ACTIVE) {
            val gap = (now - session.lastActivityAt).coerceAtLeast(0L)
            active += min(gap, SESSION_IDLE_TIMEOUT_MS)
            if (gap > SESSION_IDLE_TIMEOUT_MS) paused += gap - SESSION_IDLE_TIMEOUT_MS
        } else if (session.status == SessionStatus.PAUSED) {
            val pausedStart = session.pausedAt ?: session.lastActivityAt + SESSION_IDLE_TIMEOUT_MS
            paused += (now - pausedStart).coerceAtLeast(0L)
        }
        return session.copy(
            activeDurationMs = active,
            pausedDurationMs = paused,
            status = status,
            completedAt = if (status == SessionStatus.COMPLETED) now else null,
            endedAt = now,
            pausedAt = null
        )
    }

    private fun appendSession(raw: String, session: SavedSession): String {
        val current = decodeSessions(raw).toMutableList()
        if (current.none { it.id == session.id }) current.add(0, session)
        return encodeSessions(current.take(500))
    }

    private fun decodeSessions(raw: String): List<SavedSession> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                decodeSession(item)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun decodeOpenSession(raw: String): SavedSession? = runCatching {
        if (raw.isBlank()) null else decodeSession(JSONObject(raw))
    }.getOrNull()

    private fun decodeSession(item: JSONObject): SavedSession? {
        val dhikr = item.optString("dhikr", "").trim()
        if (dhikr.isEmpty()) return null

        val completedAt = item.optLong("completedAt").takeIf { it > 0L }
        val endedAt = item.optLong("endedAt").takeIf { it > 0L }
        val startedAt = item.optLong("startedAt").takeIf { it > 0L } ?: 0L
        val lastActivityAt = item.optLong("lastActivityAt").takeIf { it > 0L }
            ?: startedAt.takeIf { it > 0L }
            ?: completedAt
            ?: 0L
        val id = item.optString("id", "").takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        val duration = if (item.has("activeDurationMs") && !item.isNull("activeDurationMs")) {
            item.optLong("activeDurationMs").coerceAtLeast(0L)
        } else null
        val pausedAt = item.optLong("pausedAt").takeIf { it > 0L }

        return SavedSession(
            id = id,
            dhikr = dhikr,
            count = item.optInt("count").coerceAtLeast(0),
            target = item.optInt("target", 33).coerceIn(1, 9999),
            startedAt = startedAt,
            lastActivityAt = lastActivityAt,
            arabic = item.optString("arabic", ""),
            transliteration = item.optString("transliteration", ""),
            meaning = item.optString("meaning", ""),
            activeDurationMs = duration,
            pausedDurationMs = item.optLong("pausedDurationMs").coerceAtLeast(0L),
            pausedAt = pausedAt,
            status = SessionStatus.fromStoredValue(item.optString("status", "COMPLETED")),
            completedAt = completedAt,
            endedAt = endedAt ?: completedAt
        )
    }

    private fun encodeSession(session: SavedSession): String = JSONObject().apply {
        put("id", session.id)
        put("dhikr", session.dhikr)
        put("count", session.count)
        put("target", session.target)
        put("startedAt", session.startedAt)
        put("lastActivityAt", session.lastActivityAt)
        put("arabic", session.arabic)
        put("transliteration", session.transliteration)
        put("meaning", session.meaning)
        put("activeDurationMs", session.activeDurationMs ?: JSONObject.NULL)
        put("pausedDurationMs", session.pausedDurationMs)
        put("pausedAt", session.pausedAt ?: JSONObject.NULL)
        put("status", session.status.name)
        put("completedAt", session.completedAt ?: JSONObject.NULL)
        put("endedAt", session.endedAt ?: JSONObject.NULL)
    }.toString()

    private fun encodeSessions(sessions: List<SavedSession>): String {
        val array = JSONArray()
        sessions.forEach { session -> array.put(JSONObject(encodeSession(session))) }
        return array.toString()
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
