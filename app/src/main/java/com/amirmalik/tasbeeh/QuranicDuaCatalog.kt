package com.amirmalik.tasbeeh

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal data class QuranicDuaDefinition(
    val id: String,
    val title: String,
    val reference: String,
    val ayahKeys: List<String>,
    val context: String
)

internal object QuranicDuaCatalog {
    val additions: List<QuranicDuaDefinition> = listOf(
        q("quran-surah-al-fatiha", "Surah al-Fatihah", "Qur'an 1:1–7",
            keys("1:1", "1:2", "1:3", "1:4", "1:5", "1:6", "1:7"),
            "The opening surah of the Qur'an, including the request for guidance to the straight path."),
        q("quran-four-qul", "4 Qul",
            "Qur'an 109:1–6; 112:1–4; 113:1–5; 114:1–6",
            keys("109:1", "109:2", "109:3", "109:4", "109:5", "109:6",
                "112:1", "112:2", "112:3", "112:4",
                "113:1", "113:2", "113:3", "113:4", "113:5",
                "114:1", "114:2", "114:3", "114:4", "114:5", "114:6"),
            "The complete surahs al-Kafirun, al-Ikhlas, al-Falaq and an-Nas, in that order."),
        q("quran-ayat-al-kursi", "Ayat al-Kursi", "Qur'an 2:255", keys("2:255"),
            "The Throne Verse, recited as its complete ayah."),
        q("quran-last-two-baqarah", "Last two ayahs of Surah al-Baqarah",
            "Qur'an 2:285–286", keys("2:285", "2:286"),
            "The final two complete ayahs of Surah al-Baqarah, recited in order.")
    )

    init {
        require(additions.size == 4)
        require(additions.map { it.id }.distinct().size == additions.size)
        require(additions.map { it.title }.distinct().size == additions.size)
        require(additions.all { definition ->
            definition.ayahKeys.isNotEmpty() &&
                QuranRecitationAudio.audioUrlsForAyahs(definition.ayahKeys).size == definition.ayahKeys.size
        }) { "Every Quran category entry must map to valid Quran Foundation ayah recordings." }
    }

    fun fallbackItems(): List<DhikrItem> = additions.map { definition ->
        DhikrItem(
            id = definition.id,
            value = definition.title,
            title = definition.title,
            category = "Quran",
            arabic = "",
            transliteration = "",
            meaning = definition.context,
            reference = definition.reference,
            quranAyahKeys = definition.ayahKeys,
            categories = listOf("Quran")
        )
    }

    private fun q(id: String, title: String, reference: String, ayahKeys: List<String>, context: String) =
        QuranicDuaDefinition(id, title, reference, ayahKeys, context)

    private fun keys(vararg values: String) = values.toList()
}

internal data class QuranicCatalogRefreshResult(
    val items: List<DhikrItem>,
    val isComplete: Boolean,
    val refreshedFromNetwork: Boolean,
    val cachePersisted: Boolean
)

internal fun hasCompleteQuranicText(items: List<DhikrItem>): Boolean {
    if (items.size != QuranicDuaCatalog.additions.size) return false
    val itemsById = items.associateBy { it.id }
    return QuranicDuaCatalog.additions.all { definition ->
        val item = itemsById[definition.id]
        item != null && item.arabic.isNotBlank() &&
            item.transliteration.isNotBlank() &&
            item.quranAyahKeys == definition.ayahKeys
    }
}

internal fun hasAllQuranicTextAndTranslations(
    definitions: List<QuranicDuaDefinition>,
    versesByKey: Map<String, Triple<String, String, String>>
): Boolean = definitions.all { definition ->
    definition.ayahKeys.all { key ->
        versesByKey[key]?.let { (arabic, transliteration, translation) ->
            arabic.isNotBlank() && transliteration.isNotBlank() && translation.isNotBlank()
        } == true
    }
}

internal fun hasCompleteQuranicCache(
    items: List<DhikrItem>,
    completedAyahKeysById: Map<String, List<String>>
): Boolean {
    if (!hasCompleteQuranicText(items)) return false
    val itemsById = items.associateBy { it.id }
    return QuranicDuaCatalog.additions.all { definition ->
        val item = itemsById[definition.id]
        item != null &&
            (completedAyahKeysById[definition.id] == null ||
                completedAyahKeysById[definition.id] == definition.ayahKeys) &&
            item.quranAyahKeys == definition.ayahKeys
    }
}

internal class QuranicDuaRepository(context: Context) {
    private val preferences = context.getSharedPreferences("quran_catalog_v2", Context.MODE_PRIVATE)

    fun loadCachedItems(): List<DhikrItem> = runCatching {
        val raw = preferences.getString("items", null)
            ?: return@runCatching QuranicDuaCatalog.fallbackItems()
        if (raw.length > MAX_QURAN_CACHE_CHARS) return@runCatching QuranicDuaCatalog.fallbackItems()

        val array = JSONArray(raw)
        if (array.length() != QuranicDuaCatalog.additions.size) {
            return@runCatching QuranicDuaCatalog.fallbackItems()
        }
        val completedKeys = mutableMapOf<String, List<String>>()
        val cached = ArrayList<DhikrItem>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
                ?: return@runCatching QuranicDuaCatalog.fallbackItems()
            val keys = item.optJSONArray("ayahKeys") ?: return@runCatching QuranicDuaCatalog.fallbackItems()
            val id = item.optString("id")
            if (item.has("completedAyahKeys")) {
                val completed = item.optJSONArray("completedAyahKeys")
                    ?: return@runCatching QuranicDuaCatalog.fallbackItems()
                completedKeys[id] = (0 until completed.length()).map { completed.optString(it) }
            }
            cached.add(
                DhikrItem(
                    id = id,
                    value = item.optString("value"),
                    title = item.optString("title"),
                    category = "Quran",
                    arabic = item.optString("arabic"),
                    transliteration = item.optString("transliteration"),
                    meaning = item.optString("meaning"),
                    reference = item.optString("reference"),
                    quranAyahKeys = (0 until keys.length()).map { keys.optString(it) },
                    categories = listOf("Quran")
                )
            )
        }
        cached.takeIf { hasCompleteQuranicCache(it, completedKeys) }
            ?: QuranicDuaCatalog.fallbackItems()
    }.getOrDefault(QuranicDuaCatalog.fallbackItems())

    suspend fun refresh(): QuranicCatalogRefreshResult = withContext(Dispatchers.IO) {
        val chapters = QuranicDuaCatalog.additions.flatMap { it.ayahKeys }
            .mapNotNull { it.substringBefore(':').toIntOrNull() }
            .distinct().sorted()
        val verseByKey = mutableMapOf<String, Triple<String, String, String>>()

        // Limit concurrency while avoiding serial network waits across all chapters.
        chapters.chunked(4).forEach { batch ->
            val responses = coroutineScope {
                batch.map { chapter -> async { fetchChapter(chapter) } }.awaitAll()
            }
            responses.filterNotNull().forEach { verseByKey.putAll(it) }
        }

        // Never publish or cache a partial refresh. Retain a complete saved copy instead.
        val complete = hasAllQuranicTextAndTranslations(QuranicDuaCatalog.additions, verseByKey)
        if (!complete) {
            val cached = loadCachedItems()
            val cachedComplete = hasCompleteQuranicText(cached)
            return@withContext QuranicCatalogRefreshResult(
                items = if (cachedComplete) cached else QuranicDuaCatalog.fallbackItems(),
                isComplete = cachedComplete,
                refreshedFromNetwork = false,
                cachePersisted = cachedComplete
            )
        }

        val items = QuranicDuaCatalog.additions.map { definition ->
            val verses = definition.ayahKeys.mapNotNull(verseByKey::get)
            DhikrItem(
                id = definition.id,
                value = definition.title,
                title = definition.title,
                category = "Quran",
                arabic = verses.joinToString(" ۞ ") { it.first },
                transliteration = verses.joinToString(" ") { it.second },
                meaning = verses.joinToString(" ") { it.third },
                reference = definition.reference +
                    " · Arabic: AlQuran Cloud (Uthmani) · Transliteration: AlQuran Cloud · Translation: Sahih International",
                quranAyahKeys = definition.ayahKeys,
                categories = listOf("Quran")
            )
        }

        val persisted = runCatching {
            val array = JSONArray()
            items.forEach { item ->
                array.put(JSONObject().apply {
                    put("id", item.id)
                    put("value", item.value)
                    put("title", item.title)
                    put("arabic", item.arabic)
                    put("transliteration", item.transliteration)
                    put("meaning", item.meaning)
                    put("reference", item.reference)
                    put("ayahKeys", JSONArray(item.quranAyahKeys))
                    put("completedAyahKeys", JSONArray(item.quranAyahKeys))
                })
            }
            preferences.edit().putString("items", array.toString()).commit()
        }.getOrDefault(false)

        QuranicCatalogRefreshResult(
            items = items,
            isComplete = true,
            refreshedFromNetwork = true,
            cachePersisted = persisted
        )
    }

    private fun fetchChapter(chapter: Int): Map<String, Triple<String, String, String>>? {
        val address = "https://api.alquran.cloud/v1/surah/$chapter/editions/quran-uthmani,en.sahih,en.transliteration"
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            useCaches = false
            connectTimeout = 7_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "SereneDhikr/1.0")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val payload = readUtf8WithLimit(connection.inputStream, MAX_QURAN_SURAH_RESPONSE_BYTES)
            val editions = JSONObject(payload).optJSONArray("data") ?: return null
            if (editions.length() < 3) return null
            val arabicAyahs = editions.optJSONObject(0)?.optJSONArray("ayahs") ?: return null
            val translationAyahs = editions.optJSONObject(1)?.optJSONArray("ayahs") ?: return null
            val transliterationAyahs = editions.optJSONObject(2)?.optJSONArray("ayahs") ?: return null

            fun textByAyah(ayahs: JSONArray): Map<Int, String> = buildMap {
                for (index in 0 until ayahs.length()) {
                    val ayah = ayahs.optJSONObject(index) ?: continue
                    val number = ayah.optInt("numberInSurah", -1)
                    val value = ayah.optString("text").trim()
                        .replace(Regex("<[^>]*>"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (number > 0 && value.isNotBlank()) put(number, value)
                }
            }

            val transliterations = textByAyah(transliterationAyahs)
            val translations = textByAyah(translationAyahs)
            buildMap {
                for (index in 0 until arabicAyahs.length()) {
                    val ayah = arabicAyahs.optJSONObject(index) ?: continue
                    val number = ayah.optInt("numberInSurah", -1)
                    val arabic = ayah.optString("text").trim()
                    val transliteration = transliterations[number].orEmpty()
                    val translation = translations[number].orEmpty()
                    if (number > 0 && arabic.isNotBlank() &&
                        transliteration.isNotBlank() && translation.isNotBlank()
                    ) {
                        put("$chapter:$number", Triple(arabic, transliteration, translation))
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
