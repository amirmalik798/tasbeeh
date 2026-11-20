package com.amirmalik.tasbeeh.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
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
    TARGET_REACHED,
    PAUSED,
    STALE_SELECTION
}

internal fun shouldUseLegacyCountOnlyRecovery(
    rawCurrentSession: String,
    hasRecoverableLegacySession: Boolean,
    legacyCount: Int
): Boolean = rawCurrentSession.isBlank() && !hasRecoverableLegacySession && legacyCount > 0

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

internal fun safeAppFontScale(value: Float): Float =
    if (value.isFinite()) value.coerceIn(1.0f, 1.25f) else DEFAULT_APP_FONT_SCALE

private const val MAX_FAVORITE_ITEM_IDS = 1000
private const val MAX_FAVORITE_ITEM_ID_LENGTH = 180

private fun parseFavoriteItemIds(raw: String, rejectInvalidEntries: Boolean): Set<String> {
    if (raw.isBlank()) return emptySet()
    val array = JSONArray(raw)
    val ids = LinkedHashSet<String>(array.length())
    for (index in 0 until array.length()) {
        val id = array.optString(index).trim()
        if (id.isBlank() || id.length > MAX_FAVORITE_ITEM_ID_LENGTH) {
            if (rejectInvalidEntries) {
                throw IllegalStateException("Saved favorites contain an invalid item identity.")
            }
            continue
        }
        ids.add(id)
        if (ids.size >= MAX_FAVORITE_ITEM_IDS) break
    }
    return ids
}

internal fun decodeFavoriteItemIds(raw: String): Set<String> =
    runCatching { parseFavoriteItemIds(raw, rejectInvalidEntries = false) }.getOrDefault(emptySet())

internal fun encodeFavoriteItemIds(ids: Collection<String>): String =
    JSONArray().apply {
        ids.asSequence().map(String::trim)
            .filter { it.isNotBlank() && it.length <= MAX_FAVORITE_ITEM_ID_LENGTH }
            .distinct().sorted().take(MAX_FAVORITE_ITEM_IDS)
            .forEach { put(it) }
    }.toString()

internal fun toggleFavoriteItemId(current: Set<String>, itemId: String): Set<String> {
    val id = itemId.trim()
    if (id.isBlank() || id.length > MAX_FAVORITE_ITEM_ID_LENGTH) return current
    return if (id in current) current - id
    else if (current.size >= MAX_FAVORITE_ITEM_IDS) current
    else current + id
}

internal fun uniqueCustomDhikrEntries(entries: List<CustomDhikr>): List<CustomDhikr> =
    entries.distinctBy { it.id }

enum class SessionEventType { STARTED, PAUSED, RESUMED, ENDED }

data class SessionEvent(val type: SessionEventType, val at: Long)

internal const val MAX_SESSION_TIMELINE_EVENTS = 200

internal fun safeDurationSum(first: Long, second: Long): Long {
    val left = first.coerceAtLeast(0L)
    val right = second.coerceAtLeast(0L)
    return if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}

/** Keep the original start and the most recent timeline events, never an unbounded history. */
internal fun boundedSessionEvents(events: List<SessionEvent>): List<SessionEvent> {
    if (events.size <= MAX_SESSION_TIMELINE_EVENTS) return events
    val startIndex = events.indexOfFirst { it.type == SessionEventType.STARTED }
    if (startIndex < 0) return events.takeLast(MAX_SESSION_TIMELINE_EVENTS)
    return listOf(events[startIndex]) +
        events.filterIndexed { index, _ -> index != startIndex }
            .takeLast(MAX_SESSION_TIMELINE_EVENTS - 1)
}

internal fun appendSessionEvent(events: List<SessionEvent>, event: SessionEvent): List<SessionEvent> =
    boundedSessionEvents(events + event)

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
        val saved = durationMs?.coerceAtLeast(0L) ?: accumulatedActiveDuration(this)
        return if (status == SessionStatus.ACTIVE && endedAt == null && activeSegmentStartedAt != null) {
            safeDurationSum(saved, (now - activeSegmentStartedAt).coerceAtLeast(0L))
        } else saved
    }
}

internal fun accumulatedActiveDuration(session: SavedSession): Long =
    session.durationMs?.coerceAtLeast(0L)
        ?: accumulatedActiveDurationFromEvents(session.events, session.startedAt, session.endedAt)

internal fun accumulatedActiveDurationFromEvents(
    events: List<SessionEvent>,
    startedAt: Long,
    endedAt: Long?
): Long {
    if (events.isEmpty()) {
        return if (startedAt > 0L && endedAt != null) {
            (endedAt - startedAt).coerceAtLeast(0L)
        } else 0L
    }
    return events.zipWithNext().fold(0L) { total, (first, second) ->
        val interval = if (
            first.type == SessionEventType.STARTED || first.type == SessionEventType.RESUMED
        ) (second.at - first.at).coerceAtLeast(0L) else 0L
        safeDurationSum(total, interval)
    }
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
        durationMs = safeDurationSum(
            accumulatedActiveDuration(session),
            if (session.status == SessionStatus.ACTIVE && session.activeSegmentStartedAt != null) {
                (now - session.activeSegmentStartedAt).coerceAtLeast(0L)
            } else 0L
        ),
        activeSegmentStartedAt = null,
        events = appendSessionEvent(session.events, SessionEvent(SessionEventType.ENDED, now))
    )
}

data class CustomDhikr(
    val id: String,
    val title: String,
    val arabic: String = "",
    val transliteration: String = "",
    val meaning: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // Transient editor value only. The phrase itself is stored in noBackupFilesDir,
    // never in the DataStore JSON or Android's app-backup set.
    val voiceReferenceText: String = "",
    val voiceProfileId: String = "",
    val voiceProfileSourceHash: String = "",
    val voiceProfileCreatedAt: Long = 0L
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
    val dhikr: String = "Surah al-Fatihah",
    val soundEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val fontScale: Float = DEFAULT_APP_FONT_SCALE,
    val quranReciterId: Int = 1,
    val currentSession: SavedSession? = null,
    val customDhikrs: List<CustomDhikr> = emptyList(),
    val selectedCustomDhikrId: String? = null,
    val favoriteItemIds: Set<String> = emptySet()
)

class TasbeehStore(private val context: Context) {
    private object Keys {
        val count = intPreferencesKey("count")
        val target = intPreferencesKey("target")
        val dhikr = stringPreferencesKey("dhikr")
        val sound = booleanPreferencesKey("sound")
        val haptics = booleanPreferencesKey("haptics")
        val legacyTtsVoicePreference = stringPreferencesKey("tts_voice_preference")
        val fontScale = floatPreferencesKey("font_scale")
        val quranReciterId = intPreferencesKey("quran_reciter_id")
        val currentSession = stringPreferencesKey("current_session")

        // Legacy keys are read once during migration, then removed.
        val legacySessions = stringPreferencesKey("sessions")
        val legacyOpenSession = stringPreferencesKey("open_session")
        val legacyLastCompletedCount = intPreferencesKey("last_completed_count")
        val legacyLastCompletedDhikr = stringPreferencesKey("last_completed_dhikr")

        val customDhikrs = stringPreferencesKey("custom_dhikrs")
        val selectedCustomDhikrId = stringPreferencesKey("selected_custom_dhikr_id")
        val favoriteItemIds = stringPreferencesKey("favorite_item_ids")
    }

    val state: Flow<TasbeehState> = context.tasbeehDataStore.data.map { prefs ->
        val currentSession = decodeSession(prefs[Keys.currentSession].orEmpty())
        TasbeehState(
            count = visibleCounterValue(prefs[Keys.count] ?: 0, currentSession?.status),
            target = (prefs[Keys.target] ?: 33).coerceIn(1, 9999),
            dhikr = prefs[Keys.dhikr].orEmpty(),
            soundEnabled = prefs[Keys.sound] ?: false,
            hapticsEnabled = prefs[Keys.haptics] ?: true,
            fontScale = safeAppFontScale(prefs[Keys.fontScale] ?: DEFAULT_APP_FONT_SCALE),
            quranReciterId = (prefs[Keys.quranReciterId] ?: 1).coerceAtLeast(1),
            currentSession = currentSession,
            customDhikrs = decodeCustomDhikrs(prefs[Keys.customDhikrs].orEmpty()),
            selectedCustomDhikrId = prefs[Keys.selectedCustomDhikrId],
            favoriteItemIds = decodeFavoriteItemIds(prefs[Keys.favoriteItemIds].orEmpty())
        )
    }.flowOn(Dispatchers.Default)

    /**
     * Migrates only the current/open session. Older historical sessions are not retained.
     */
    suspend fun migrateToSingleSession() {
        context.tasbeehDataStore.edit { prefs ->
            val rawCurrentSession = prefs[Keys.currentSession].orEmpty()
            if (decodeSession(rawCurrentSession) == null) {
                // Prefer a readable legacy copy when the current representation
                // is damaged. Do not erase an unreadable non-empty record when
                // there is nothing safe to recover; the caller's recovery screen
                // then keeps the original DataStore value intact for diagnosis.
                val legacyOpen = decodeLegacySession(prefs[Keys.legacyOpenSession].orEmpty())
                val latestLegacy = decodeLegacySessions(prefs[Keys.legacySessions].orEmpty()).firstOrNull()
                val legacyCount = (prefs[Keys.count] ?: 0).coerceAtLeast(0)
                val hasRecoverableLegacySession = legacyOpen != null || latestLegacy != null
                val recoverCountOnly = shouldUseLegacyCountOnlyRecovery(
                    rawCurrentSession,
                    hasRecoverableLegacySession,
                    legacyCount
                )
                val recovered = legacyOpen ?: latestLegacy ?: if (recoverCountOnly) {
                    // The former counter did not always create a session record.
                    // Keep its count, but do not invent a historical start time.
                    SavedSession(
                        id = UUID.randomUUID().toString(),
                        dhikr = prefs[Keys.dhikr] ?: "Surah al-Fatihah",
                        count = legacyCount,
                        target = (prefs[Keys.target] ?: 33).coerceIn(1, 9999),
                        startedAt = 0L,
                        status = SessionStatus.ACTIVE
                    )
                } else null

                when {
                    recovered != null -> prefs[Keys.currentSession] = encodeSession(recovered)
                    rawCurrentSession.isBlank() -> prefs.remove(Keys.currentSession)
                    else -> throw IllegalStateException(
                        "The saved session is unreadable and no legacy session could be recovered."
                    )
                }
            }

            prefs.remove(Keys.legacySessions)
            prefs.remove(Keys.legacyOpenSession)
            prefs.remove(Keys.legacyLastCompletedCount)
            prefs.remove(Keys.legacyLastCompletedDhikr)
            // Remove the old gendered TTS preference during the one-time migration.
            prefs.remove(Keys.legacyTtsVoicePreference)
        }
    }

    suspend fun update(
        count: Int? = null,
        target: Int? = null,
        dhikr: String? = null,
        soundEnabled: Boolean? = null,
        hapticsEnabled: Boolean? = null,
        fontScale: Float? = null,
        quranReciterId: Int? = null
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
            fontScale?.let { prefs[Keys.fontScale] = safeAppFontScale(it) }
            quranReciterId?.takeIf { it > 0 }?.let { prefs[Keys.quranReciterId] = it }
        }
    }

    /**
     * Adds or removes a stable library entry ID from the user's Favorites.
     * Only the identity is stored; Quran and OpenDua content stays in its
     * existing catalogue/cache.
     */
    suspend fun toggleFavorite(itemId: String) {
        val normalizedId = itemId.trim()
        require(normalizedId.isNotBlank() && normalizedId.length <= MAX_FAVORITE_ITEM_ID_LENGTH) {
            "A valid library item identity is required."
        }
        context.tasbeehDataStore.edit { prefs ->
            val current = parseFavoriteItemIds(
                prefs[Keys.favoriteItemIds].orEmpty(),
                rejectInvalidEntries = true
            )
            prefs[Keys.favoriteItemIds] = encodeFavoriteItemIds(
                toggleFavoriteItemId(current, normalizedId)
            )
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
        now: Long,
        expectedCustomDhikrId: String? = null
    ): CountOutcome {
        var outcome = CountOutcome.COUNTED
        context.tasbeehDataStore.edit { prefs ->
            if (expectedCustomDhikrId != null &&
                prefs[Keys.selectedCustomDhikrId] != expectedCustomDhikrId
            ) {
                outcome = CountOutcome.STALE_SELECTION
                return@edit
            }
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
            val reachedTarget = nextCount >= target
            val saved = if (reachedTarget) {
                outcome = CountOutcome.TARGET_REACHED
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
                durationMs = safeDurationSum(
                    accumulatedActiveDuration(session),
                    session.activeSegmentStartedAt?.let { (at - it).coerceAtLeast(0L) } ?: 0L
                ),
                activeSegmentStartedAt = null,
                events = appendSessionEvent(session.events, SessionEvent(SessionEventType.PAUSED, at))
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
                events = appendSessionEvent(session.events, SessionEvent(SessionEventType.RESUMED, at))
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

    /**
     * Leaves the counter without an active selection. The current session is
     * preserved as incomplete before clearing, so legacy built-in Quran or
     * OpenDua counts are not silently continued by the personal counter.
     */
    suspend fun clearDhikrSelection(now: Long = System.currentTimeMillis()) {
        context.tasbeehDataStore.edit { prefs ->
            finishOpenSessionIfNeeded(prefs, now)
            prefs[Keys.count] = 0
            prefs[Keys.dhikr] = ""
            prefs.remove(Keys.selectedCustomDhikrId)
        }
    }

    suspend fun selectCustomDhikr(dhikr: CustomDhikr, now: Long = System.currentTimeMillis()) {
        if (dhikr.id.isBlank()) return
        context.tasbeehDataStore.edit { prefs ->
            val selected = decodeCustomDhikrsForMutation(prefs[Keys.customDhikrs].orEmpty())
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
            meaning = dhikr.meaning.trim().take(500),
            voiceReferenceText = "",
            voiceProfileId = dhikr.voiceProfileId.trim().take(36),
            voiceProfileSourceHash = dhikr.voiceProfileSourceHash.trim().take(64),
            voiceProfileCreatedAt = dhikr.voiceProfileCreatedAt.coerceAtLeast(0L)
        )
        if (cleaned.id.isBlank() || cleaned.title.isBlank()) return

        context.tasbeehDataStore.edit { prefs ->
            val current = decodeCustomDhikrsForMutation(prefs[Keys.customDhikrs].orEmpty()).toMutableList()
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
            val remaining = decodeCustomDhikrsForMutation(prefs[Keys.customDhikrs].orEmpty())
                .filterNot { it.id == id }
            prefs[Keys.customDhikrs] = encodeCustomDhikrs(remaining)
            if (prefs[Keys.selectedCustomDhikrId] == id) {
                finishOpenSessionIfNeeded(prefs, now)
                prefs[Keys.count] = 0
                prefs[Keys.dhikr] = ""
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
                    item.optLong("durationMs").takeIf { it >= 0L }
                } else null
                val decodedEvents = decodeEvents(item.optJSONArray("events"))
                // Older versions allowed an unlimited event list. Compact it on
                // read while capturing its full accumulated duration first.
                val recoveredDuration = if (duration == null &&
                    decodedEvents.size > MAX_SESSION_TIMELINE_EVENTS
                ) accumulatedActiveDurationFromEvents(decodedEvents, startedAt, endedAt) else null
                val sessionEvents = if (decodedEvents.isEmpty()) {
                    legacySessionEvents(startedAt, endedAt)
                } else {
                    boundedSessionEvents(decodedEvents)
                }
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
                    durationMs = duration ?: recoveredDuration,
                    activeSegmentStartedAt = item.optLong("activeSegmentStartedAt").takeIf { it > 0L }
                        ?: if (item.optString("status") == SessionStatus.ACTIVE.name && endedAt == null) startedAt.takeIf { it > 0L } else null,
                    events = sessionEvents
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

    private fun decodeCustomDhikrs(raw: String): List<CustomDhikr> =
        runCatching { parseCustomDhikrs(raw, rejectInvalidEntries = false) }
            .getOrDefault(emptyList())

    /**
     * A failed display read must never be mistaken for an empty collection by
     * a later save/delete. Mutations use this strict decoder and fail before
     * writing if the original collection cannot be fully understood.
     */
    private fun decodeCustomDhikrsForMutation(raw: String): List<CustomDhikr> =
        parseCustomDhikrs(raw, rejectInvalidEntries = true)

    private fun parseCustomDhikrs(
        raw: String,
        rejectInvalidEntries: Boolean
    ): List<CustomDhikr> {
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        val seenIds = HashSet<String>(array.length())
        val entries = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                if (item == null) {
                    if (rejectInvalidEntries) {
                        throw IllegalStateException("Saved custom Dhikr collection contains an invalid entry.")
                    }
                    continue
                }

                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                if (id.isEmpty() || title.isEmpty()) {
                    if (rejectInvalidEntries) {
                        throw IllegalStateException("Saved custom Dhikr entry is missing its identity or name.")
                    }
                    continue
                }
                // An ID is the stable identity used by Compose list keys and
                // selection. Keep the first copy so duplicate persisted rows
                // cannot crash the list or make a valid item undeletable.
                if (!seenIds.add(id)) continue

                add(
                    CustomDhikr(
                        id = id,
                        title = title.take(60),
                        arabic = item.optString("arabic").take(800),
                        transliteration = item.optString("transliteration").take(800),
                        meaning = item.optString("meaning").take(500),
                        createdAt = item.optLong("createdAt"),
                        voiceProfileId = item.optString("voiceProfileId").take(36),
                        voiceProfileSourceHash = item.optString("voiceProfileSourceHash").take(64),
                        voiceProfileCreatedAt = item.optLong("voiceProfileCreatedAt").coerceAtLeast(0L)
                    )
                )
            }
        }
        return if (rejectInvalidEntries) entries else uniqueCustomDhikrEntries(entries)
    }

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
                    put("voiceProfileId", dhikr.voiceProfileId)
                    put("voiceProfileSourceHash", dhikr.voiceProfileSourceHash)
                    put("voiceProfileCreatedAt", dhikr.voiceProfileCreatedAt)
                }
            )
        }
        return array.toString()
    }
}
