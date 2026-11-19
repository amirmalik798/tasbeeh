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

/**
 * A single saved session. Duration is the wall-clock time from start to finish,
 * so it naturally includes any pauses between those timestamps.
 */
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
    val durationMs: Long? = null
) {
    fun durationAt(now: Long): Long? = sessionDurationMillis(startedAt, endedAt, now)
}

internal fun sessionDurationMillis(startedAt: Long, endedAt: Long?, now: Long): Long? {
    if (startedAt <= 0L) return null
    return ((endedAt ?: now) - startedAt).coerceAtLeast(0L)
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
        durationMs = sessionDurationMillis(session.startedAt, now, now)
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

data class TasbeehState(
    val count: Int = 0,
    val target: Int = 33,
    val dhikr: String = "SubhanAllah",
    val soundEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
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
        TasbeehState(
            count = (prefs[Keys.count] ?: 0).coerceAtLeast(0),
            target = (prefs[Keys.target] ?: 33).coerceIn(1, 9999),
            dhikr = prefs[Keys.dhikr] ?: "SubhanAllah",
            soundEnabled = prefs[Keys.sound] ?: false,
            hapticsEnabled = prefs[Keys.haptics] ?: true,
            fontScale = (prefs[Keys.fontScale] ?: DEFAULT_APP_FONT_SCALE).coerceIn(1.0f, 1.25f),
            currentSession = decodeSession(prefs[Keys.currentSession].orEmpty()),
            customDhikrs = decodeCustomDhikrs(prefs[Keys.customDhikrs].orEmpty()),
            selectedCustomDhikrId = prefs[Keys.selectedCustomDhikrId]
        )
    }

    /**
     * Converts older session-list storage to one current-session record.
     * The open session takes precedence; otherwise only the newest saved
     * history entry is retained. Older entries are then removed.
     */
    suspend fun migrateToSingleSession() {
        context.tasbeehDataStore.edit { prefs ->
            if (prefs[Keys.currentSession].isNullOrBlank()) {
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

                recovered?.let { prefs[Keys.currentSession] = encodeSession(it) }
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
        fontScale: Float? = null
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
            fontScale?.let { prefs[Keys.fontScale] = it.coerceIn(1.0f, 1.25f) }
        }
    }

    /**
     * The count and its single session snapshot are saved atomically. There
     * is no per-tap history append and no scan over old sessions.
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
            val nextCount = baseCount + 1

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
                existing!!.copy(
                    count = nextCount,
                    // A legacy recovered session may not have a known start time.
                    startedAt = existing.startedAt.takeIf { it > 0L } ?: now
                )
            }

            prefs[Keys.count] = nextCount
            val saved = if (nextCount >= target) {
                finishSession(session, now, SessionStatus.COMPLETED)
            } else {
                session.copy(status = SessionStatus.ACTIVE, endedAt = null, durationMs = null)
            }
            prefs[Keys.currentSession] = encodeSession(saved)
        }
        return outcome
    }

    suspend fun undoCount(now: Long) {
        context.tasbeehDataStore.edit { prefs ->
            val currentCount = (prefs[Keys.count] ?: 0).coerceAtLeast(0)
            if (currentCount == 0) return@edit

            val session = decodeSession(prefs[Keys.currentSession].orEmpty())
            if (session?.status == SessionStatus.PAUSED || session?.status == SessionStatus.COMPLETED) {
                return@edit
            }

            val nextCount = (currentCount - 1).coerceAtLeast(0)
            prefs[Keys.count] = nextCount
            if (session != null) {
                if (nextCount == 0) {
                    prefs.remove(Keys.currentSession)
                } else {
                    prefs[Keys.currentSession] = encodeSession(
                        session.copy(count = nextCount, status = SessionStatus.ACTIVE, endedAt = null, durationMs = null)
                    )
                }
            }
        }
    }

    suspend fun pauseCurrentSession() {
        context.tasbeehDataStore.edit { prefs ->
            val session = decodeSession(prefs[Keys.currentSession].orEmpty()) ?: return@edit
            if (session.status != SessionStatus.ACTIVE || session.count <= 0) return@edit
            prefs[Keys.currentSession] = encodeSession(session.copy(status = SessionStatus.PAUSED))
        }
    }

    suspend fun resumeCurrentSession() {
        context.tasbeehDataStore.edit { prefs ->
            val session = decodeSession(prefs[Keys.currentSession].orEmpty()) ?: return@edit
            if (session.status != SessionStatus.PAUSED) return@edit
            prefs[Keys.currentSession] = encodeSession(session.copy(status = SessionStatus.ACTIVE))
        }
    }

    suspend fun finishCurrentSession(now: Long, resetCounter: Boolean = true) {
        context.tasbeehDataStore.edit { prefs ->
            val session = decodeSession(prefs[Keys.currentSession].orEmpty())
            if (session != null &&
                (session.status == SessionStatus.ACTIVE || session.status == SessionStatus.PAUSED)
            ) {
                prefs[Keys.currentSession] = encodeSession(
                    finishSession(session, now, SessionStatus.INCOMPLETE)
                )
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
                    prefs[Keys.currentSession] = encodeSession(
                        finishSession(session!!, now, SessionStatus.INCOMPLETE)
                    )
                }
                !saveSession && isOpen -> prefs.remove(Keys.currentSession)
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
            prefs[Keys.currentSession] = encodeSession(
                finishSession(session, now, SessionStatus.INCOMPLETE)
            )
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
            durationMs = duration
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
                    durationMs = duration
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
    }.toString()

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
