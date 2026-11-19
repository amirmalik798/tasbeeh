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
    val sessions: List<SavedSession> = emptyList(),
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
        val sessions = stringPreferencesKey("sessions")
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
            sessions = decodeSessions(prefs[Keys.sessions].orEmpty()),
            customDhikrs = decodeCustomDhikrs(prefs[Keys.customDhikrs].orEmpty()),
            selectedCustomDhikrId = prefs[Keys.selectedCustomDhikrId]
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
            if (existingIndex >= 0) {
                current[existingIndex] = cleaned
            } else {
                current.add(0, cleaned)
            }
            prefs[Keys.customDhikrs] = encodeCustomDhikrs(current)
            if (prefs[Keys.selectedCustomDhikrId] == cleaned.id) {
                // Keep the active selection in sync when its name is edited.
                prefs[Keys.dhikr] = cleaned.title
            }
        }
    }

    suspend fun selectBuiltInDhikr(value: String) {
        if (value.isBlank()) return
        context.tasbeehDataStore.edit { prefs ->
            prefs[Keys.count] = 0
            prefs[Keys.dhikr] = value
            prefs.remove(Keys.selectedCustomDhikrId)
        }
    }

    suspend fun selectCustomDhikr(id: String) {
        if (id.isBlank()) return
        context.tasbeehDataStore.edit { prefs ->
            val selected = decodeCustomDhikrs(prefs[Keys.customDhikrs].orEmpty())
                .firstOrNull { it.id == id } ?: return@edit
            prefs[Keys.count] = 0
            prefs[Keys.dhikr] = selected.title
            prefs[Keys.selectedCustomDhikrId] = selected.id
        }
    }

    suspend fun deleteCustomDhikr(id: String) {
        if (id.isBlank()) return
        context.tasbeehDataStore.edit { prefs ->
            val remaining = decodeCustomDhikrs(prefs[Keys.customDhikrs].orEmpty())
                .filterNot { it.id == id }
            prefs[Keys.customDhikrs] = encodeCustomDhikrs(remaining)
            if (prefs[Keys.selectedCustomDhikrId] == id) {
                // Deleting the active custom entry returns safely to the default.
                prefs[Keys.count] = 0
                prefs[Keys.dhikr] = "SubhanAllah"
                prefs.remove(Keys.selectedCustomDhikrId)
            }
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
