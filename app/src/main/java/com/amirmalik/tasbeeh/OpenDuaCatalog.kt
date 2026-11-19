package com.amirmalik.tasbeeh

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OpenDua's pinned, versioned public catalogue. The catalogue contains the
 * chapter taxonomy, canonical dua blocks and the exact recording URLs that
 * cover those blocks. We keep a private copy so selection remains available
 * offline after the first successful sync.
 *
 * Dataset: https://opendua.org/v0.0.4/catalogue.json
 * API and release documentation: https://opendua.hdfund.org/developers/
 */
internal object OpenDuaCatalog {
    const val DATASET_VERSION = "0.0.4"
    const val DATASET_URL = "https://opendua.org/v0.0.4/catalogue.json"
    const val ATTRIBUTION =
        "OpenDua dataset v0.0.4 by Ihsan Labs at the Human Development Fund. " +
            "OpenDua editorial text, dataset structure and Muhammad Jumah recitations " +
            "are licensed CC BY 4.0; underlying religious texts are not claimed."

    private const val MAX_CATALOGUE_BYTES = 2 * 1024 * 1024

    suspend fun load(context: Context): List<DhikrItem> = withContext(Dispatchers.IO) {
        val cacheFile = File(File(context.filesDir, "opendua"), "catalogue-$DATASET_VERSION.json")
        val cached = runCatching {
            if (cacheFile.isFile && cacheFile.length() in 1..MAX_CATALOGUE_BYTES.toLong()) {
                cacheFile.readText(Charsets.UTF_8)
            } else null
        }.getOrNull()

        val raw = runCatching { downloadCatalogue() }.getOrNull()?.also { downloaded ->
            runCatching {
                cacheFile.parentFile?.mkdirs()
                val temporary = File(cacheFile.parentFile, cacheFile.name + ".tmp")
                temporary.writeText(downloaded, Charsets.UTF_8)
                if (!temporary.renameTo(cacheFile)) {
                    temporary.copyTo(cacheFile, overwrite = true)
                    temporary.delete()
                }
            }
        } ?: cached ?: return@withContext emptyList()

        runCatching { parse(raw) }.getOrElse { emptyList() }
    }

    private fun downloadCatalogue(): String {
        val connection = (URL(DATASET_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 25_000
            useCaches = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "SereneDhikr-Android")
        }
        try {
            val status = connection.responseCode
            check(status == HttpURLConnection.HTTP_OK) { "OpenDua returned HTTP $status" }
            val declaredLength = connection.contentLengthLong
            require(declaredLength <= MAX_CATALOGUE_BYTES) { "OpenDua catalogue exceeds the size limit" }
            val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            require(raw.toByteArray(Charsets.UTF_8).size <= MAX_CATALOGUE_BYTES) {
                "OpenDua catalogue exceeds the size limit"
            }
            return raw
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(raw: String): List<DhikrItem> {
        val root = JSONObject(raw)
        require(root.optString("dataVersion") == DATASET_VERSION) {
            "Unexpected OpenDua dataset version"
        }
        val chapters = root.optJSONArray("chapters") ?: JSONArray()
        val chapterTitles = buildMap<String, String> {
            for (i in 0 until chapters.length()) {
                val chapter = chapters.optJSONObject(i) ?: continue
                val id = chapter.optString("id")
                val title = chapter.optString("title")
                if (id.isNotBlank() && title.isNotBlank()) put(id, title)
            }
        }
        val entries = root.optJSONArray("entries") ?: JSONArray()
        val seenDuaIds = HashSet<String>()
        val result = ArrayList<DhikrItem>(262)

        for (entryIndex in 0 until entries.length()) {
            val entry = entries.optJSONObject(entryIndex) ?: continue
            if (entry.optString("type") != "invocation") continue

            val entryTitle = entry.optString("title").trim()
            if (entryTitle.isBlank()) continue
            val category = chapterTitles[entry.optString("chapterId")]
                ?: entry.optJSONArray("tags")?.optString(0)?.replace('-', ' ')
                    ?.replaceFirstChar { it.uppercase() }
                ?: "Daily Du'a"
            val sourceReference = entry.optString("sourceReference").trim()
                .takeIf { it.isNotBlank() }
            val variations = entry.optJSONArray("variations") ?: continue
            val preferredVariation = (0 until variations.length())
                .mapNotNull { variations.optJSONObject(it) }
                .firstOrNull { it.optString("id") == "standard" }
                ?: variations.optJSONObject(0)
                ?: continue
            val steps = preferredVariation.optJSONArray("steps") ?: continue
            var itemNumber = 0

            for (stepIndex in 0 until steps.length()) {
                val step = steps.optJSONObject(stepIndex) ?: continue
                if (step.optString("type") != "recitation") continue
                val items = step.optJSONArray("items") ?: continue
                val recordings = step.optJSONArray("recordings") ?: JSONArray()
                val audioUrls = buildList {
                    for (recordingIndex in 0 until recordings.length()) {
                        val recording = recordings.optJSONObject(recordingIndex) ?: continue
                        val uri = recording.optString("url").trim()
                        if (uri.startsWith("https://audio.opendua.org/") && uri.endsWith(".mp3")) {
                            add(uri)
                        }
                    }
                }.distinct()

                for (itemIndex in 0 until items.length()) {
                    val item = items.optJSONObject(itemIndex) ?: continue
                    val dua = item.optJSONObject("dua") ?: continue
                    val duaId = dua.optString("id").trim()
                    if (duaId.isBlank() || !seenDuaIds.add(duaId)) continue
                    val arabic = dua.optString("arabic").trim()
                    val transliteration = dua.optString("transliteration").trim()
                    val meaning = dua.optString("translation").trim()
                    if (arabic.isBlank() || transliteration.isBlank() || meaning.isBlank()) continue

                    itemNumber++
                    val reference = buildString {
                        append("OpenDua v")
                        append(DATASET_VERSION)
                        append(" · Hisn al-Muslim")
                        if (sourceReference != null) {
                            append(' ')
                            append(sourceReference)
                        }
                    }
                    result += DhikrItem(
                        id = "opendua-$duaId",
                        value = "$entryTitle · $duaId",
                        title = if (items.length() > 1) "$entryTitle ($itemNumber)" else entryTitle,
                        category = category,
                        arabic = arabic,
                        transliteration = transliteration,
                        meaning = meaning,
                        reference = reference,
                        recitationUrls = audioUrls,
                        recitationCredit = "Recitation by Muhammad Jumah for OpenDua"
                    )
                }
            }
        }

        // The pinned release documents 262 canonical duas. Reject incomplete or
        // structurally incompatible responses instead of presenting a silently
        // truncated library as complete.
        require(result.size >= 250) {
            "OpenDua catalogue was incomplete: parsed ${result.size} canonical duas"
        }
        return result
    }
}
