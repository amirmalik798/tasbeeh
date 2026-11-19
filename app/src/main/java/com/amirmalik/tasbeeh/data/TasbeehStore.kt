package com.amirmalik.tasbeeh.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.tasbeehDataStore by preferencesDataStore(name = "tasbeeh_state")

data class SavedSession(val id: Long, val dhikr: String, val count: Int, val target: Int, val completedAt: Long)
data class TasbeehState(
    val count: Int = 0,
    val target: Int = 33,
    val dhikr: String = "SubhanAllah",
    val soundEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val sessions: List<SavedSession> = emptyList()
)

class TasbeehStore(private val context: Context) {
    private object Keys {
        val count = intPreferencesKey("count")
        val target = intPreferencesKey("target")
        val dhikr = stringPreferencesKey("dhikr")
        val sound = booleanPreferencesKey("sound")
        val haptics = booleanPreferencesKey("haptics")
        val sessions = stringPreferencesKey("sessions")
    }

    val state: Flow<TasbeehState> = context.tasbeehDataStore.data.map { prefs ->
        TasbeehState(
            count = (prefs[Keys.count] ?: 0).coerceAtLeast(0),
            target = (prefs[Keys.target] ?: 33).coerceIn(1, 9999),
            dhikr = prefs[Keys.dhikr] ?: "SubhanAllah",
            soundEnabled = prefs[Keys.sound] ?: false,
            hapticsEnabled = prefs[Keys.haptics] ?: true,
            sessions = decodeSessions(prefs[Keys.sessions].orEmpty())
        )
    }

    suspend fun update(count: Int? = null, target: Int? = null, dhikr: String? = null, soundEnabled: Boolean? = null, hapticsEnabled: Boolean? = null) {
        context.tasbeehDataStore.edit { prefs ->
            count?.let { prefs[Keys.count] = it.coerceAtLeast(0) }
            target?.let { prefs[Keys.target] = it.coerceIn(1, 9999) }
            dhikr?.let { prefs[Keys.dhikr] = it }
            soundEnabled?.let { prefs[Keys.sound] = it }
            hapticsEnabled?.let { prefs[Keys.haptics] = it }
        }
    }

    suspend fun completeSession(session: SavedSession) {
        context.tasbeehDataStore.edit { prefs ->
            val current = decodeSessions(prefs[Keys.sessions].orEmpty()).toMutableList()
            if (current.none { it.id == session.id }) current.add(0, session)
            prefs[Keys.sessions] = encodeSessions(current.take(100))
        }
    }

    private fun decodeSessions(raw: String): List<SavedSession> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(SavedSession(item.optLong("id"), item.optString("dhikr", "SubhanAllah"),
                    item.optInt("count").coerceAtLeast(0), item.optInt("target").coerceAtLeast(1), item.optLong("completedAt")))
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeSessions(sessions: List<SavedSession>): String {
        val array = JSONArray()
        sessions.forEach { s ->
            array.put(JSONObject().apply {
                put("id", s.id); put("dhikr", s.dhikr); put("count", s.count)
                put("target", s.target); put("completedAt", s.completedAt)
            })
        }
        return array.toString()
    }
}
