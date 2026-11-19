package com.amirmalik.tasbeeh

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Quran Foundation's public ayah-by-ayah recitation catalogue and URL resolver.
 *
 * The selected recitation ID is the provider's stable resource ID. Audio URL
 * paths are resolved for the selected ID, chapter and verse instead of
 * guessing a CDN path from the reciter's display name.
 */
internal data class QuranReciter(
    val id: Int,
    val name: String,
    val style: String?
) {
    val displayName: String
        get() = if (style.isNullOrBlank()) name else "$name · $style"

    val attribution: String
        get() = "Qur'anic recitation by $displayName · Quran.Foundation"
}

internal object QuranReciterCatalog {
    const val DEFAULT_RECITER_ID = 1

    // Kept as an offline fallback for first launch and temporary API outages.
    // This is the ayah-by-ayah list returned by Quran Foundation's
    // /resources/recitations endpoint (styles are separate choices).
    val fallback: List<QuranReciter> = listOf(
        QuranReciter(2, "AbdulBaset AbdulSamad", "Murattal"),
        QuranReciter(1, "AbdulBaset AbdulSamad", "Mujawwad"),
        QuranReciter(3, "Abdur-Rahman as-Sudais", null),
        QuranReciter(4, "Abu Bakr al-Shatri", null),
        QuranReciter(5, "Hani ar-Rifai", null),
        QuranReciter(12, "Mahmoud Khalil Al-Husary", "Muallim"),
        QuranReciter(6, "Mahmoud Khalil Al-Husary", null),
        QuranReciter(7, "Mishari Rashid al-'Afasy", null),
        QuranReciter(9, "Mohamed Siddiq al-Minshawi", "Murattal"),
        QuranReciter(8, "Mohamed Siddiq al-Minshawi", "Mujawwad"),
        QuranReciter(10, "Sa'ud ash-Shuraym", null),
        QuranReciter(11, "Mohamed al-Tablawi", null)
    )

    fun parse(payload: String): List<QuranReciter> = runCatching {
        val array = JSONObject(payload).optJSONArray("recitations") ?: return emptyList()
        if (array.length() !in 1..MAX_RECITERS) return emptyList()
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optInt("id", -1)
                val name = item.optString("reciter_name").trim()
                    .ifBlank { item.optJSONObject("translated_name")?.optString("name").orEmpty().trim() }
                val style = item.optString("style").trim().takeUnless {
                    it.isBlank() || it.equals("null", ignoreCase = true)
                }
                if (id > 0 && name.isNotBlank()) add(QuranReciter(id, name, style))
            }
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    fun encode(reciters: List<QuranReciter>): String = JSONArray().apply {
        reciters.forEach { reciter ->
            put(JSONObject().apply {
                put("id", reciter.id)
                put("name", reciter.name)
                put("style", reciter.style ?: JSONObject.NULL)
            })
        }
    }.toString()

    fun decode(encoded: String?): List<QuranReciter> {
        if (encoded.isNullOrBlank() || encoded.length > MAX_CACHE_CHARS) return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            if (array.length() !in 1..MAX_RECITERS) return emptyList()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optInt("id", -1)
                    val name = item.optString("name").trim()
                    val style = item.optString("style").takeUnless {
                        it.isBlank() || it.equals("null", ignoreCase = true)
                    }
                    if (id > 0 && name.isNotBlank()) add(QuranReciter(id, name, style))
                }
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    private const val MAX_RECITERS = 500
    private const val MAX_CACHE_CHARS = 100_000
}

internal class QuranRecitationRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun cachedReciters(): List<QuranReciter> =
        QuranReciterCatalog.decode(preferences.getString(KEY_RECITERS, null))
            .ifEmpty { QuranReciterCatalog.fallback }

    suspend fun refreshReciters(): List<QuranReciter> = withContext(Dispatchers.IO) {
        val refreshed = runCatching {
            val response = getJson("$API_BASE/resources/recitations?language=en")
            QuranReciterCatalog.parse(response.toString()).also { reciters ->
                if (reciters.isEmpty()) throw IOException("Quran Foundation returned an empty reciter list.")
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not refresh the Quran Foundation reciter catalogue; retaining cached choices", error)
        }.getOrDefault(emptyList())

        if (refreshed.isNotEmpty()) {
            preferences.edit().putString(KEY_RECITERS, QuranReciterCatalog.encode(refreshed)).apply()
            refreshed
        } else {
            cachedReciters()
        }
    }

    /**
     * Resolve every requested verse to the audio URL for the selected QF
     * recitation ID. Chapters are fetched in bounded 50-verse pages, then
     * mapped back to the caller's original ayah order.
     *
     * An incomplete response is rejected instead of starting a partial surah.
     */
    suspend fun audioUrlsForAyahs(recitationId: Int, ayahKeys: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            if (recitationId <= 0 || ayahKeys.isEmpty() || ayahKeys.size > MAX_REQUESTED_AYAHS) {
                return@withContext emptyList()
            }
            if (ayahKeys.any { QuranRecitationAudio.audioUrlForAyah(it) == null }) {
                return@withContext emptyList()
            }

            val keysByChapter = ayahKeys.groupBy { it.substringBefore(':').toInt() }
            val urlsByKey = mutableMapOf<String, String>()
            for ((chapter, requestedKeys) in keysByChapter) {
                val chapterUrls = fetchChapterUrls(recitationId, chapter) ?: return@withContext emptyList()
                requestedKeys.forEach { key ->
                    val url = chapterUrls[key] ?: return@withContext emptyList()
                    urlsByKey[key] = url
                }
            }

            val ordered = ayahKeys.map { urlsByKey[it] ?: return@withContext emptyList() }
            if (ordered.any { !isTrustedRecitationUrl(it) }) emptyList() else ordered
        }

    private fun fetchChapterUrls(recitationId: Int, chapter: Int): Map<String, String>? {
        val ayahCount = QuranRecitationAudio.ayahCountForSurah(chapter)
        if (ayahCount <= 0) return null

        val pageCount = (ayahCount + PAGE_SIZE - 1) / PAGE_SIZE
        val urls = HashMap<String, String>(ayahCount)
        for (page in 1..pageCount) {
            val endpoint = "$API_BASE/recitations/$recitationId/by_chapter/$chapter?per_page=$PAGE_SIZE&page=$page"
            val response = getJson(endpoint)
            val files = response.optJSONArray("audio_files") ?: return null
            for (index in 0 until files.length()) {
                val file = files.optJSONObject(index) ?: continue
                val verseKey = file.optString("verse_key").trim()
                if (!verseKey.startsWith("$chapter:")) continue
                val rawUrl = file.optString("url").trim()
                    .ifBlank { file.optString("audio_url").trim() }
                val normalized = normalizeAudioUrl(rawUrl) ?: return null
                if (!isTrustedRecitationUrl(normalized)) return null
                urls[verseKey] = normalized
            }
            if (files.length() < PAGE_SIZE && page < pageCount) {
                // An early final page is acceptable only if every ayah is present.
                break
            }
        }
        return urls.takeIf { it.size == ayahCount }
    }

    private fun normalizeAudioUrl(value: String): String? {
        if (value.isBlank() || value.length > MAX_AUDIO_URL_CHARS) return null
        val normalized = when {
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("http://", ignoreCase = true) -> return null
            value.startsWith("/") -> "https://verses.quran.foundation$value"
            else -> "https://verses.quran.foundation/$value"
        }
        return normalized.takeIf(::isTrustedRecitationUrl)
    }

    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "SereneDhikr/1.0")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                runCatching { connection.errorStream?.close() }
                throw IOException("Quran Foundation request failed with HTTP $status.")
            }
            // Enforce the limit while reading, before allocating the full response.
            // Checking String.length after readText() would be too late to prevent
            // an unexpectedly large provider response from exhausting memory.
            val body = connection.inputStream.use { input ->
                readUtf8WithLimit(input, MAX_QURAN_SURAH_RESPONSE_BYTES)
            }
            if (body.isBlank()) {
                throw IOException("Quran Foundation returned an empty audio response.")
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "QuranRecitationRepo"
        const val PREFERENCES_NAME = "quran_recitation_catalogue"
        const val KEY_RECITERS = "reciters_json"
        const val API_BASE = "https://api.quran.com/api/v4"
        const val PAGE_SIZE = 50
        const val MAX_REQUESTED_AYAHS = 700
        const val MAX_AUDIO_URL_CHARS = 2_048
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 18_000
    }
}
