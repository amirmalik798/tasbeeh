package com.amirmalik.tasbeeh

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OpenDua API v2 reader. The dataset version and audio origin are pinned from
 * API metadata, and the last successful catalogue is cached locally so the
 * library remains available when the network is unavailable.
 *
 * The public API currently documents a staging preview; its review statuses
 * are not treated as a claim of scholarly approval.
 */
internal class OpenDuaCatalogRepository(context: Context) {
    private val preferences = context.getSharedPreferences("open_dua_catalog", Context.MODE_PRIVATE)

    fun loadCachedItems(): List<DhikrItem> {
        val raw = preferences.getString("items_v2", null) ?: return emptyList()
        return runCatching { decodeItems(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    suspend fun refresh(): List<DhikrItem> = withContext(Dispatchers.IO) {
        val metadata = requestJson(API_BASE + "/v2")
        val meta = metadata.optJSONObject("meta") ?: JSONObject()
        val datasetVersion = meta.optString("datasetVersion")
            .takeIf { Regex("\\d+\\.\\d+\\.\\d+").matches(it) }
        val audioBaseUrl = meta.optString("audioBaseUrl")
            .takeIf { it.startsWith("https://") } ?: DEFAULT_AUDIO_BASE

        val collected = LinkedHashMap<String, DhikrItem>()
        var cursor: String? = null
        var pagesRead = 0
        do {
            val query = buildString {
                append(API_BASE + "/v2/duas?limit=100")
                datasetVersion?.let { append("&version=").append(URLEncoder.encode(it, "UTF-8")) }
                cursor?.let { append("&cursor=").append(URLEncoder.encode(it, "UTF-8")) }
            }
            val page = requestJson(query)
            val pageMeta = page.optJSONObject("meta")
            val resolvedAudioBase = pageMeta?.optString("audioBaseUrl")
                ?.takeIf { it.startsWith("https://") } ?: audioBaseUrl
            val data = page.optJSONArray("data") ?: JSONArray()
            for (index in 0 until data.length()) {
                val entry = data.optJSONObject(index) ?: continue
                val parsed = parseOpenDuaEntry(entry, resolvedAudioBase) ?: continue
                if (!collected.containsKey(parsed.id)) collected[parsed.id] = parsed
                if (collected.size >= TARGET_COUNT) break
            }
            cursor = page.optJSONObject("pagination")?.optString("nextCursor")
                ?.takeIf { it.isNotBlank() && it != "null" }
            pagesRead++
        } while (cursor != null && collected.size < TARGET_COUNT && pagesRead < MAX_PAGES)

        val items = collected.values.toList().take(TARGET_COUNT)
        if (items.size == TARGET_COUNT) {
            runCatching {
                val array = JSONArray()
                items.forEach { item -> array.put(encodeItem(item)) }
                preferences.edit()
                    .putString("items_v2", array.toString())
                    .putString("dataset_version", datasetVersion.orEmpty())
                    .apply()
            }
        }
        if (items.isNotEmpty()) items else loadCachedItems()
    }

    internal fun parseOpenDuaEntry(entry: JSONObject, audioBaseUrl: String): DhikrItem? {
        if (entry.optString("form") != "dua") return null
        val title = entry.optString("title").trim()
        val slug = entry.optString("slug").trim().ifBlank { entry.optString("id").trim() }
        if (title.isBlank() || slug.isBlank()) return null

        val source = entry.optJSONObject("source")
        val sourceName = source?.optString("workName").orEmpty()
        val sourceId = source?.optString("workId").orEmpty()
        val sourceCitation = source?.optString("citation").orEmpty()
        val tagsForSource = entry.optJSONArray("tags") ?: JSONArray()
        val isQuranicSource = sourceName.contains("qur'an", ignoreCase = true) ||
            sourceName.contains("quran", ignoreCase = true) ||
            sourceId.contains("quran", ignoreCase = true) ||
            sourceCitation.contains("qur'an", ignoreCase = true) ||
            sourceCitation.contains("quran", ignoreCase = true) ||
            (0 until tagsForSource.length()).any {
                tagsForSource.optString(it).contains("quran", ignoreCase = true)
            }
        if (isQuranicSource) return null

        val blocks = entry.optJSONArray("blocks") ?: return null
        val recitationBlocks = buildList {
            for (index in 0 until blocks.length()) {
                val block = blocks.optJSONObject(index) ?: continue
                if (block.optString("type") != "recitation") continue
                if (block.optString("arabic").isNotBlank()) add(block)
            }
        }
        if (recitationBlocks.isEmpty()) return null

        val blockIds = recitationBlocks.map { it.optString("id") }.filter { it.isNotBlank() }.toSet()
        val arabic = recitationBlocks.joinToString(" ۞ ") { it.optString("arabic").trim() }
        val transliteration = recitationBlocks.map { it.optString("transliteration").trim() }
            .filter { it.isNotBlank() }.joinToString(" · ")
        val meaning = recitationBlocks.map { it.optString("english").trim() }
            .filter { it.isNotBlank() }.joinToString(" ")

        val recordings = entry.optJSONArray("recordings") ?: JSONArray()
        val audioUrls = buildList {
            for (index in 0 until recordings.length()) {
                val recording = recordings.optJSONObject(index) ?: continue
                val covers = recording.optJSONArray("covers")
                val coversSelectedBlocks = covers == null || covers.length() == 0 ||
                    (0 until covers.length()).any { covers.optString(it) in blockIds }
                if (!coversSelectedBlocks) continue
                val objectKey = recording.optString("objectKey").trim().trimStart('/')
                if (objectKey.isBlank()) continue
                add(audioBaseUrl.trimEnd('/') + "/" + objectKey)
            }
        }.distinct()

        val collection = entry.optJSONArray("collections")?.optJSONObject(0)
        val tags = entry.optJSONArray("tags")
        val category = collection?.optString("name")?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: tags?.optString(0)?.takeIf { it.isNotBlank() }?.replace('-', ' ')?.replaceFirstChar { it.uppercase() }
            ?: "Daily Du'a"
        val citation = sourceCitation.takeIf { it.isNotBlank() }
            ?: "OpenDua " + entry.optString("id")
        val id = entry.optString("id").ifBlank { slug }

        return DhikrItem(
            id = "opendua-" + slug,
            // The counter/session store uses the display title as its stable lookup value.
            // OpenDua IDs remain the Compose list identity; API titles are distinct variants.
            value = title,
            title = title,
            category = category,
            arabic = arabic,
            transliteration = transliteration,
            meaning = meaning,
            reference = citation + " · OpenDua",
            recitationAudioUrls = audioUrls
        )
    }

    private fun requestJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 9_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NOOR-E-DHIKR/1.0")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("OpenDua API returned HTTP " + status)
            return JSONObject(payload)
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeItems(array: JSONArray): List<DhikrItem> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val urls = item.optJSONArray("audioUrls") ?: JSONArray()
            add(
                DhikrItem(
                    id = item.optString("id"),
                    value = item.optString("value"),
                    title = item.optString("title"),
                    category = item.optString("category"),
                    arabic = item.optString("arabic"),
                    transliteration = item.optString("transliteration"),
                    meaning = item.optString("meaning"),
                    reference = item.optString("reference"),
                    recitationAudioUrls = (0 until urls.length()).map { urls.optString(it) }
                )
            )
        }
    }

    private fun encodeItem(item: DhikrItem) = JSONObject().apply {
        put("id", item.id)
        put("value", item.value)
        put("title", item.title)
        put("category", item.category)
        put("arabic", item.arabic)
        put("transliteration", item.transliteration)
        put("meaning", item.meaning)
        put("reference", item.reference)
        put("audioUrls", JSONArray(item.recitationAudioUrls))
    }

    companion object {
        private const val API_BASE = "https://api.staging.opendua.org"
        private const val DEFAULT_AUDIO_BASE = "https://audio.staging.opendua.org"
        private const val TARGET_COUNT = 87
        private const val MAX_PAGES = 8
    }
}
