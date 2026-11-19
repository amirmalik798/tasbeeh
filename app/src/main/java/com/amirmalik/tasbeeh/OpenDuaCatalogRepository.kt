package com.amirmalik.tasbeeh

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class OpenDuaCatalogSnapshot(
    val items: List<DhikrItem>,
    val categories: List<String>,
    val dataVersion: String,
    val canonicalDuaCount: Int = 0,
    val recordingCount: Int = 0,
    val cachePersisted: Boolean = true
)

internal fun openDuaCacheChecksum(
    version: String,
    rawItems: String,
    rawCategories: String,
    sourceEntryCount: Int,
    canonicalDuaCount: Int,
    recordingCount: Int
): String {
    val cachePayload = listOf(
        version,
        sourceEntryCount.toString(),
        canonicalDuaCount.toString(),
        recordingCount.toString(),
        rawItems,
        rawCategories
    ).joinToString(separator = "\n")
    return MessageDigest.getInstance("SHA-256")
        .digest(cachePayload.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

/**
 * Parser for OpenDua's immutable, versioned catalogue release.
 *
 * Each published entry is retained. Context-specific variations are exposed
 * as separate selectable options so their wording and recording stay paired.
 * Instruction and narration records remain visible, but are not countable.
 */
internal object OpenDuaCatalogParser {
    const val DATA_VERSION = "0.0.4"
    const val DATASET_URL = "https://opendua.org/v0.0.4/catalogue.json"
    const val EXPECTED_ENTRY_COUNT = 267
    const val EXPECTED_DUA_COUNT = 262
    const val EXPECTED_RECORDING_COUNT = 249
    const val EXPECTED_CATEGORY_COUNT = 19

    fun parse(root: JSONObject): OpenDuaCatalogSnapshot? {
        if (root.optString("dataVersion") != DATA_VERSION) return null

        val counts = root.optJSONObject("counts") ?: return null
        // Entry count is the catalogue completeness contract. Dua and recording
        // totals are metadata and must not discard otherwise valid entries.
        if (counts.optInt("entries", -1) != EXPECTED_ENTRY_COUNT) return null

        val tags = root.optJSONArray("tags") ?: return null
        val categoryById = LinkedHashMap<String, String>()
        val categories = buildList {
            for (index in 0 until tags.length()) {
                val tag = tags.optJSONObject(index) ?: continue
                val id = tag.optString("id").trim()
                val title = tag.optString("title").trim()
                if (id.isNotBlank() && title.isNotBlank()) {
                    categoryById[id] = title
                    if (title !in this) add(title)
                }
            }
        }
        if (categories.size != EXPECTED_CATEGORY_COUNT) return null

        val reciters = root.optJSONArray("reciters") ?: JSONArray()
        val reciterNames = buildMap<String, String> {
            for (index in 0 until reciters.length()) {
                val reciter = reciters.optJSONObject(index) ?: continue
                val id = reciter.optString("id").trim()
                val name = reciter.optString("name").trim()
                if (id.isNotBlank() && name.isNotBlank()) put(id, name)
            }
        }

        val chapters = root.optJSONArray("chapters") ?: JSONArray()
        val chapterTitles = buildMap<String, String> {
            for (index in 0 until chapters.length()) {
                val chapter = chapters.optJSONObject(index) ?: continue
                val id = chapter.optString("id").trim()
                val title = chapter.optString("title").trim()
                if (id.isNotBlank() && title.isNotBlank()) put(id, title)
            }
        }

        val entries = root.optJSONArray("entries") ?: return null
        val items = ArrayList<DhikrItem>()
        val sourceEntryIds = linkedSetOf<String>()
        val canonicalDuaIds = linkedSetOf<String>()
        val recordingIds = linkedSetOf<String>()
        val selectionValues = linkedSetOf<String>()

        for (entryIndex in 0 until entries.length()) {
            val entry = entries.optJSONObject(entryIndex) ?: return null
            val entryId = entry.optString("id").trim()
            val sourceTitle = entry.optString("title").trim()
            if (entryId.isBlank() || sourceTitle.isBlank()) return null
            sourceEntryIds += entryId

            val entryType = entry.optString("type").trim().ifBlank { "invocation" }
            val entryCategories = buildList {
                val tagIds = entry.optJSONArray("tags") ?: JSONArray()
                for (tagIndex in 0 until tagIds.length()) {
                    val tagId = tagIds.optString(tagIndex).trim()
                    val label = categoryById[tagId]
                        ?: tagId.replace('-', ' ').replaceFirstChar { it.uppercase() }
                    if (label.isNotBlank() && label !in this) add(label)
                }
            }

            val variations = entry.optJSONArray("variations")
            val variants: List<JSONObject?> = if (variations != null && variations.length() > 0) {
                buildList {
                    for (variantIndex in 0 until variations.length()) {
                        add(variations.optJSONObject(variantIndex) ?: return null)
                    }
                }
            } else {
                listOf(null)
            }

            variants.forEachIndexed { variantIndex, variation ->
                val variationId = variation?.optString("id")?.trim()
                    ?.takeIf { it.isNotBlank() } ?: "default-$variantIndex"
                val variationLabel = variation?.optString("label")?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: variation?.optString("condition")?.trim()?.takeIf { it.isNotBlank() }
                    ?: variationId.takeUnless { it == "standard" || it == "default-$variantIndex" }

                val steps = variation?.optJSONArray("steps")
                    ?: entry.optJSONArray("steps")
                    ?: JSONArray()
                val arabicParts = mutableListOf<String>()
                val transliterationParts = mutableListOf<String>()
                val meaningParts = mutableListOf<String>()
                val audioUrls = linkedSetOf<String>()
                val creditNames = linkedSetOf<String>()

                for (stepIndex in 0 until steps.length()) {
                    val step = steps.optJSONObject(stepIndex) ?: continue
                    when (step.optString("type")) {
                        "recitation" -> {
                            val duaItems = step.optJSONArray("items") ?: JSONArray()
                            for (duaIndex in 0 until duaItems.length()) {
                                val wrapper = duaItems.optJSONObject(duaIndex) ?: continue
                                val dua = wrapper.optJSONObject("dua") ?: wrapper
                                val duaId = dua.optString("id").trim()
                                if (duaId.isNotBlank()) canonicalDuaIds += duaId
                                dua.optString("arabic").trim().takeIf { it.isNotBlank() }
                                    ?.let(arabicParts::add)
                                dua.optString("transliteration").trim().takeIf { it.isNotBlank() }
                                    ?.let(transliterationParts::add)
                                dua.optString("translation").trim().takeIf { it.isNotBlank() }
                                    ?.let(meaningParts::add)
                            }

                            val recordings = step.optJSONArray("recordings") ?: JSONArray()
                            for (recordingIndex in 0 until recordings.length()) {
                                val recording = recordings.optJSONObject(recordingIndex) ?: continue
                                val url = recording.optString("url").trim()
                                if (!isTrustedOpenDuaAudioUrl(url)) continue
                                audioUrls += url
                                recording.optString("id").trim().takeIf { it.isNotBlank() }
                                    ?.let(recordingIds::add)
                                val reciterId = recording.optString("reciterId").trim()
                                reciterNames[reciterId]?.let(creditNames::add)
                            }
                        }
                        "instruction", "narration" -> {
                            step.optString("arabic").trim().takeIf { it.isNotBlank() }
                                ?.let(arabicParts::add)
                            step.optString("text").trim().takeIf { it.isNotBlank() }
                                ?.let(meaningParts::add)
                        }
                    }
                }

                val displayTitle = if (variationLabel == null) sourceTitle
                    else "$sourceTitle — $variationLabel"
                var selectionValue = displayTitle
                if (selectionValue in selectionValues) {
                    val sourceRef = entry.optString("sourceReference").trim()
                    selectionValue = "$displayTitle · ${
                        sourceRef.ifBlank { entryId }
                    }"
                }
                if (selectionValue in selectionValues) {
                    selectionValue = "$selectionValue · $variationId"
                }
                if (!selectionValues.add(selectionValue)) return null

                val referenceParts = buildList {
                    val sourceReference = entry.optString("sourceReference").trim()
                    if (sourceReference.isNotBlank()) add("Hisn al-Muslim #${sourceReference}")
                    chapterTitles[entry.optString("chapterId")]?.let(::add)
                    variationLabel?.let(::add)
                }
                val reference = (listOf("OpenDua v$DATA_VERSION") + referenceParts)
                    .joinToString(" · ")

                val itemCategory = entryCategories.firstOrNull().orEmpty()
                items += DhikrItem(
                    id = "opendua-$entryId-$variationId",
                    value = selectionValue,
                    title = displayTitle,
                    category = itemCategory,
                    arabic = arabicParts.distinct().joinToString(" ۞ "),
                    transliteration = transliterationParts.distinct().joinToString(" · "),
                    meaning = meaningParts.joinToString("\n\n"),
                    reference = reference,
                    recitationAudioUrls = audioUrls.toList(),
                    recitationCredit = creditNames.joinToString(" · ")
                        .ifBlank { if (audioUrls.isNotEmpty()) "OpenDua recorded recitation" else "" },
                    categories = entryCategories,
                    sourceEntryId = entryId,
                    contentType = entryType,
                    isCountable = entryType == "invocation"
                )
            }
        }

        // Refuse partial or malformed releases. The app keeps its last complete
        // cached release rather than silently displaying a truncated library.
        if (sourceEntryIds.size != EXPECTED_ENTRY_COUNT ||
            categories.size != EXPECTED_CATEGORY_COUNT ||
            items.isEmpty() ||
            items.map { it.id }.distinct().size != items.size
        ) return null

        return OpenDuaCatalogSnapshot(
            items = items,
            categories = categories,
            dataVersion = DATA_VERSION,
            canonicalDuaCount = canonicalDuaIds.size,
            recordingCount = recordingIds.size
        )
    }
}

internal class OpenDuaCatalogRepository(context: Context) {
    private val preferences = context.getSharedPreferences("open_dua_catalog_v004", Context.MODE_PRIVATE)

    fun loadCachedSnapshot(): OpenDuaCatalogSnapshot {
        val version = OpenDuaCatalogParser.DATA_VERSION
        fun emptySnapshot() = OpenDuaCatalogSnapshot(emptyList(), emptyList(), version)

        // A damaged preference value must never crash the library screen.
        return runCatching {
            if (preferences.getString("data_version", null) != version) {
                return@runCatching emptySnapshot()
            }
            val rawItems = preferences.getString("items", null)
                ?: return@runCatching emptySnapshot()
            val rawCategories = preferences.getString("categories", null)
                ?: return@runCatching emptySnapshot()
            if (rawItems.length > MAX_OPENDUA_CACHE_CHARS ||
                rawCategories.length > 16 * 1024
            ) return@runCatching emptySnapshot()
            val storedChecksum = preferences.getString("cache_sha256", null)
            val sourceEntryCount = preferences.getInt("source_entry_count", 0)
            val canonicalDuaCount = preferences.getInt("canonical_dua_count", 0)
            val recordingCount = preferences.getInt("recording_count", 0)

            if (sourceEntryCount != OpenDuaCatalogParser.EXPECTED_ENTRY_COUNT ||
                canonicalDuaCount !in 1..OpenDuaCatalogParser.EXPECTED_DUA_COUNT ||
                recordingCount !in 0..OpenDuaCatalogParser.EXPECTED_RECORDING_COUNT
            ) return@runCatching emptySnapshot()

            val calculatedChecksum = openDuaCacheChecksum(
                version,
                rawItems,
                rawCategories,
                sourceEntryCount,
                canonicalDuaCount,
                recordingCount
            )
            if (storedChecksum != null && !MessageDigest.isEqual(
                    storedChecksum.toByteArray(Charsets.US_ASCII),
                    calculatedChecksum.toByteArray(Charsets.US_ASCII)
                )
            ) return@runCatching emptySnapshot()

            val rawItemsArray = JSONArray(rawItems)
            val items = decodeItems(rawItemsArray)
            val categoriesArray = JSONArray(rawCategories)
            val categories = (0 until categoriesArray.length())
                .map { categoriesArray.optString(it).trim() }
                .filter { it.isNotBlank() }
            val sourceIds = items.map { it.sourceEntryId }.toSet()

            val isComplete = items.size == rawItemsArray.length() &&
                sourceIds.size == OpenDuaCatalogParser.EXPECTED_ENTRY_COUNT &&
                categories.size == OpenDuaCatalogParser.EXPECTED_CATEGORY_COUNT &&
                categories.distinct().size == categories.size &&
                items.map { it.id }.distinct().size == items.size &&
                items.all { item ->
                    item.id.isNotBlank() &&
                        item.title.isNotBlank() &&
                        item.value.isNotBlank() &&
                        item.sourceEntryId.isNotBlank() &&
                        item.categories.isNotEmpty()
                }

            if (!isComplete) {
                emptySnapshot()
            } else {
                // Migrate an older, structurally complete v0.0.4 cache by
                // recording its checksum. Do not discard a good offline copy
                // just because it was saved before checksum support existed.
                if (storedChecksum == null) {
                    preferences.edit().putString("cache_sha256", calculatedChecksum).apply()
                }
                OpenDuaCatalogSnapshot(
                    items = items,
                    categories = categories,
                    dataVersion = version,
                    canonicalDuaCount = canonicalDuaCount,
                    recordingCount = recordingCount,
                    cachePersisted = true
                )
            }
        }.getOrDefault(emptySnapshot())
    }

    fun loadCachedItems(): List<DhikrItem> = loadCachedSnapshot().items

    fun loadCachedCategories(): List<String> = loadCachedSnapshot().categories

    suspend fun refresh(): OpenDuaCatalogSnapshot = withContext(Dispatchers.IO) {
        val connection = (URL(OpenDuaCatalogParser.DATASET_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            useCaches = false
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("OpenDua catalogue returned HTTP $status")
            }
            val payload = readUtf8WithLimit(connection.inputStream, MAX_OPENDUA_RESPONSE_BYTES)
            val snapshot = OpenDuaCatalogParser.parse(JSONObject(payload))
                ?: throw IOException("OpenDua catalogue did not match the complete v${
                    OpenDuaCatalogParser.DATA_VERSION
                } release integrity checks")

            val encodedItems = JSONArray()
            snapshot.items.forEach { item -> encodedItems.put(encodeItem(item)) }
            val rawItems = encodedItems.toString()
            val rawCategories = JSONArray(snapshot.categories).toString()
            val sourceEntryCount = OpenDuaCatalogParser.EXPECTED_ENTRY_COUNT
            val checksum = openDuaCacheChecksum(
                snapshot.dataVersion,
                rawItems,
                rawCategories,
                sourceEntryCount,
                snapshot.canonicalDuaCount,
                snapshot.recordingCount
            )
            // commit() runs on Dispatchers.IO here and confirms the full snapshot
            // is on disk before it is advertised as an offline-ready cache.
            val persisted = runCatching {
                preferences.edit()
                    .putString("items", rawItems)
                    .putString("categories", rawCategories)
                    .putString("data_version", snapshot.dataVersion)
                    .putInt("source_entry_count", sourceEntryCount)
                    .putInt("canonical_dua_count", snapshot.canonicalDuaCount)
                    .putInt("recording_count", snapshot.recordingCount)
                    .putString("cache_sha256", checksum)
                    .commit()
            }.getOrDefault(false)
            snapshot.copy(cachePersisted = persisted)
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeItems(array: JSONArray): List<DhikrItem> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val categoryArray = item.optJSONArray("categories") ?: JSONArray()
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
                    recitationAudioUrls = (0 until urls.length()).map { urls.optString(it) },
                    recitationCredit = item.optString("recitationCredit"),
                    categories = (0 until categoryArray.length()).map { categoryArray.optString(it) },
                    sourceEntryId = item.optString("sourceEntryId"),
                    contentType = item.optString("contentType", "invocation"),
                    isCountable = item.optBoolean("isCountable", true)
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
        put("recitationCredit", item.recitationCredit)
        put("categories", JSONArray(item.categories))
        put("sourceEntryId", item.sourceEntryId)
        put("contentType", item.contentType)
        put("isCountable", item.isCountable)
    }
}
