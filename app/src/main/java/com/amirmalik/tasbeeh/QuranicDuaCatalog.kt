package com.amirmalik.tasbeeh

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal data class QuranicDuaDefinition(
    val id: String,
    val title: String,
    val reference: String,
    val ayahKeys: List<String>,
    val context: String
)

internal data class QuranSurahDefinition(
    val number: Int,
    val englishName: String,
    val title: String,
    val ayahKeys: List<String>
) {
    val id: String
        get() = if (number == 1) "quran-surah-al-fatiha"
        else "quran-surah-${number.toString().padStart(3, '0')}"
}

internal data class QuranAyahText(
    val number: Int,
    val arabic: String,
    val transliteration: String,
    val translation: String
)

internal data class QuranSurahText(
    val surahNumber: Int,
    val ayahs: List<QuranAyahText>
)

internal object QuranicDuaCatalog {
    const val VERSE_SEPARATOR = " ۞ "

    // Bundled names keep all 114 surahs discoverable before the first
    // successful network refresh.
    private val englishNames = """
        Al-Faatiha
        Al-Baqara
        Aal-i-Imraan
        An-Nisaa
        Al-Maaida
        Al-An'aam
        Al-A'raaf
        Al-Anfaal
        At-Tawba
        Yunus
        Hud
        Yusuf
        Ar-Ra'd
        Ibrahim
        Al-Hijr
        An-Nahl
        Al-Israa
        Al-Kahf
        Maryam
        Taa-Haa
        Al-Anbiyaa
        Al-Hajj
        Al-Muminoon
        An-Noor
        Al-Furqaan
        Ash-Shu'araa
        An-Naml
        Al-Qasas
        Al-Ankaboot
        Ar-Room
        Luqman
        As-Sajda
        Al-Ahzaab
        Saba
        Faatir
        Yaseen
        As-Saaffaat
        Saad
        Az-Zumar
        Ghafir
        Fussilat
        Ash-Shura
        Az-Zukhruf
        Ad-Dukhaan
        Al-Jaathiya
        Al-Ahqaf
        Muhammad
        Al-Fath
        Al-Hujuraat
        Qaaf
        Adh-Dhaariyat
        At-Tur
        An-Najm
        Al-Qamar
        Ar-Rahmaan
        Al-Waaqia
        Al-Hadid
        Al-Mujaadila
        Al-Hashr
        Al-Mumtahana
        As-Saff
        Al-Jumu'a
        Al-Munaafiqoon
        At-Taghaabun
        At-Talaaq
        At-Tahrim
        Al-Mulk
        Al-Qalam
        Al-Haaqqa
        Al-Ma'aarij
        Nooh
        Al-Jinn
        Al-Muzzammil
        Al-Muddaththir
        Al-Qiyaama
        Al-Insaan
        Al-Mursalaat
        An-Naba
        An-Naazi'aat
        Abasa
        At-Takwir
        Al-Infitaar
        Al-Mutaffifin
        Al-Inshiqaaq
        Al-Burooj
        At-Taariq
        Al-A'laa
        Al-Ghaashiya
        Al-Fajr
        Al-Balad
        Ash-Shams
        Al-Lail
        Ad-Dhuhaa
        Ash-Sharh
        At-Tin
        Al-Alaq
        Al-Qadr
        Al-Bayyina
        Az-Zalzala
        Al-Aadiyaat
        Al-Qaari'a
        At-Takaathur
        Al-Asr
        Al-Humaza
        Al-Fil
        Quraish
        Al-Maa'un
        Al-Kawthar
        Al-Kaafiroon
        An-Nasr
        Al-Masad
        Al-Ikhlaas
        Al-Falaq
        An-Naas
    """.trimIndent().lines()

    val surahs: List<QuranSurahDefinition> = (1..114).map { number ->
        val englishName = englishNames[number - 1]
        val keys = QuranRecitationAudio.ayahKeysForSurah(number)
        QuranSurahDefinition(
            number = number,
            englishName = englishName,
            title = if (number == 1) "Surah al-Fatihah" else "Surah $englishName",
            ayahKeys = keys
        )
    }

    // Retain the established quick selections alongside the 114 full surahs.
    val additions: List<QuranicDuaDefinition> = listOf(
        q(
            "quran-four-qul",
            "4 Qul",
            "Qur'an 109:1–6; 112:1–4; 113:1–5; 114:1–6",
            keys(
                "109:1", "109:2", "109:3", "109:4", "109:5", "109:6",
                "112:1", "112:2", "112:3", "112:4",
                "113:1", "113:2", "113:3", "113:4", "113:5",
                "114:1", "114:2", "114:3", "114:4", "114:5", "114:6"
            ),
            "The complete surahs al-Kafirun, al-Ikhlas, al-Falaq and an-Nas, in that order."
        ),
        q(
            "quran-ayat-al-kursi",
            "Ayat al-Kursi",
            "Qur'an 2:255",
            keys("2:255"),
            "The Throne Verse, recited as its complete ayah."
        ),
        q(
            "quran-last-two-baqarah",
            "Last two ayahs of Surah al-Baqarah",
            "Qur'an 2:285–286",
            keys("2:285", "2:286"),
            "The final two complete ayahs of Surah al-Baqarah, recited in order."
        )
    )

    init {
        require(englishNames.size == 114) { "The bundled Qur'an index must contain all 114 surah names." }
        require(surahs.size == 114 && surahs.all { it.ayahKeys.isNotEmpty() })
        require(surahs.map { it.id }.distinct().size == 114)
        require(additions.map { it.id }.distinct().size == additions.size)
        require(additions.all { definition ->
            definition.ayahKeys.isNotEmpty() &&
                QuranRecitationAudio.audioUrlsForAyahs(definition.ayahKeys).size == definition.ayahKeys.size
        }) { "Every quick Qur'an selection must map to valid Quran Foundation ayah recordings." }
    }

    private val surahById = surahs.associateBy { it.id }
    private val additionById = additions.associateBy { it.id }

    fun fallbackItems(): List<DhikrItem> = itemsFromSurahTexts(emptyMap())

    fun itemsFromSurahTexts(surahTexts: Map<Int, QuranSurahText>): List<DhikrItem> {
        val verseByKey = surahTexts.values.flatMap { surah ->
            surah.ayahs.map { ayah -> "${surah.surahNumber}:${ayah.number}" to ayah }
        }.toMap()

        val surahItems = surahs.map { definition ->
            val ayahs = surahTexts[definition.number]?.ayahs.orEmpty()
            DhikrItem(
                id = definition.id,
                value = definition.title,
                title = definition.title,
                category = "Quran",
                arabic = ayahs.joinToString(VERSE_SEPARATOR) { it.arabic },
                transliteration = ayahs.joinToString(VERSE_SEPARATOR) { it.transliteration },
                meaning = ayahs.joinToString(VERSE_SEPARATOR) { it.translation },
                reference = "Qur'an ${definition.number} · ${definition.ayahKeys.size} ayahs",
                quranAyahKeys = definition.ayahKeys,
                categories = listOf("Quran")
            )
        }

        val quickItems = additions.map { definition ->
            val ayahs = definition.ayahKeys.mapNotNull(verseByKey::get)
            val complete = ayahs.size == definition.ayahKeys.size &&
                ayahs.all { it.arabic.isNotBlank() && it.transliteration.isNotBlank() && it.translation.isNotBlank() }
            DhikrItem(
                id = definition.id,
                value = definition.title,
                title = definition.title,
                category = "Quran",
                arabic = if (complete) ayahs.joinToString(VERSE_SEPARATOR) { it.arabic } else "",
                transliteration = if (complete) ayahs.joinToString(VERSE_SEPARATOR) { it.transliteration } else "",
                meaning = if (complete) ayahs.joinToString(VERSE_SEPARATOR) { it.translation } else definition.context,
                reference = definition.reference,
                quranAyahKeys = definition.ayahKeys,
                categories = listOf("Quran")
            )
        }

        // Keep the previous first-four ordering and put the remaining surahs
        // in canonical order immediately after those shortcuts.
        return buildList {
            add(surahItems.first())
            addAll(quickItems)
            addAll(surahItems.drop(1))
        }
    }

    fun isFullSurah(item: DhikrItem): Boolean =
        surahById[item.id]?.ayahKeys == item.quranAyahKeys

    fun expectedAyahKeysForId(id: String): List<String>? =
        surahById[id]?.ayahKeys ?: additionById[id]?.ayahKeys

    private fun q(
        id: String,
        title: String,
        reference: String,
        ayahKeys: List<String>,
        context: String
    ) = QuranicDuaDefinition(id, title, reference, ayahKeys, context)

    private fun keys(vararg values: String) = values.toList()
}

internal data class QuranicCatalogRefreshResult(
    val items: List<DhikrItem>,
    val isComplete: Boolean,
    val refreshedFromNetwork: Boolean,
    val cachePersisted: Boolean,
    val completedSurahCount: Int = 0
)

internal fun hasCompleteQuranicText(items: List<DhikrItem>): Boolean {
    val itemsById = items.associateBy { it.id }
    val allSurahsComplete = QuranicDuaCatalog.surahs.all { definition ->
        val item = itemsById[definition.id]
        item != null &&
            item.quranAyahKeys == definition.ayahKeys &&
            item.arabic.isNotBlank() &&
            item.transliteration.isNotBlank() &&
            item.meaning.isNotBlank()
    }
    if (!allSurahsComplete) return false

    return QuranicDuaCatalog.additions.all { definition ->
        val item = itemsById[definition.id]
        item != null &&
            item.quranAyahKeys == definition.ayahKeys &&
            item.arabic.isNotBlank() &&
            item.transliteration.isNotBlank() &&
            item.meaning.isNotBlank()
    }
}

internal fun hasAllQuranicTextAndTranslations(
    definitions: List<QuranSurahDefinition>,
    versesByKey: Map<String, QuranAyahText>
): Boolean = definitions.all { definition ->
    definition.ayahKeys.all { key ->
        versesByKey[key]?.let { verse ->
            verse.arabic.isNotBlank() &&
                verse.transliteration.isNotBlank() &&
                verse.translation.isNotBlank()
        } == true
    }
}

internal class QuranicDuaRepository(context: Context) {
    private val preferences = context.getSharedPreferences("quran_catalog_v2", Context.MODE_PRIVATE)
    private val cacheDirectory = File(context.filesDir, "quran_catalog_v3")

    fun loadCachedItems(): List<DhikrItem> {
        val cachedSurahs = readAllCachedSurahs()
        return mergeLegacyCache(QuranicDuaCatalog.itemsFromSurahTexts(cachedSurahs))
    }

    suspend fun refresh(): QuranicCatalogRefreshResult = withContext(Dispatchers.IO) {
        val cachedSurahs = readAllCachedSurahs().toMutableMap()
        val initialItems = mergeLegacyCache(QuranicDuaCatalog.itemsFromSurahTexts(cachedSurahs))
        if (hasCompleteQuranicText(initialItems)) {
            return@withContext QuranicCatalogRefreshResult(
                items = initialItems,
                isComplete = true,
                refreshedFromNetwork = false,
                cachePersisted = true,
                completedSurahCount = QuranicDuaCatalog.surahs.size
            )
        }

        val missing = QuranicDuaCatalog.surahs.filterNot { cachedSurahs.containsKey(it.number) }
        val semaphore = Semaphore(4)
        val results = coroutineScope {
            missing.map { definition ->
                async {
                    semaphore.withPermit {
                        definition.number to fetchChapter(definition.number)
                    }
                }
            }.awaitAll()
        }

        var refreshedCount = 0
        var allWritesSucceeded = true
        results.forEach { (number, text) ->
            if (text != null) {
                refreshedCount += 1
                cachedSurahs[number] = text
                if (!writeSurahCache(text)) allWritesSucceeded = false
            }
        }

        val items = mergeLegacyCache(QuranicDuaCatalog.itemsFromSurahTexts(cachedSurahs))
        val complete = hasCompleteQuranicText(items)
        QuranicCatalogRefreshResult(
            items = items,
            isComplete = complete,
            refreshedFromNetwork = refreshedCount > 0,
            cachePersisted = complete && allWritesSucceeded &&
                QuranicDuaCatalog.surahs.all { cacheFile(it.number).exists() },
            completedSurahCount = cachedSurahs.size
        )
    }

    private fun readAllCachedSurahs(): Map<Int, QuranSurahText> = buildMap {
        QuranicDuaCatalog.surahs.forEach { definition ->
            readSurahCache(definition.number)?.let { put(definition.number, it) }
        }
    }

    private fun readSurahCache(number: Int): QuranSurahText? = runCatching {
        val file = cacheFile(number)
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_QURAN_SURAH_CACHE_BYTES) {
            return@runCatching null
        }

        val raw = AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(raw)
        if (root.optInt("schemaVersion", -1) != QURAN_CACHE_SCHEMA_VERSION ||
            root.optInt("surahNumber", -1) != number
        ) return@runCatching null

        val definition = QuranicDuaCatalog.surahs.getOrNull(number - 1) ?: return@runCatching null
        val array = root.optJSONArray("ayahs") ?: return@runCatching null
        if (array.length() != definition.ayahKeys.size) return@runCatching null

        val ayahs = ArrayList<QuranAyahText>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: return@runCatching null
            val expectedNumber = index + 1
            val ayahNumber = item.optInt("number", -1)
            val arabic = item.optString("arabic").trim()
            val transliteration = item.optString("transliteration").trim()
            val translation = item.optString("translation").trim()
            if (ayahNumber != expectedNumber || arabic.isBlank() ||
                transliteration.isBlank() || translation.isBlank()
            ) return@runCatching null

            ayahs.add(QuranAyahText(ayahNumber, arabic, transliteration, translation))
        }
        QuranSurahText(number, ayahs)
    }.getOrNull()

    private fun writeSurahCache(text: QuranSurahText): Boolean = runCatching {
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw IllegalStateException("Could not create the private Qur'an cache directory.")
        }
        val file = cacheFile(text.surahNumber)
        val root = JSONObject().apply {
            put("schemaVersion", QURAN_CACHE_SCHEMA_VERSION)
            put("surahNumber", text.surahNumber)
            put("ayahs", JSONArray().apply {
                text.ayahs.forEach { ayah ->
                    put(
                        JSONObject()
                            .put("number", ayah.number)
                            .put("arabic", ayah.arabic)
                            .put("transliteration", ayah.transliteration)
                            .put("translation", ayah.translation)
                    )
                }
            })
        }
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_QURAN_SURAH_CACHE_BYTES) {
            throw IllegalStateException("The cached surah exceeds the permitted size.")
        }

        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
        true
    }.getOrDefault(false)

    private fun cacheFile(number: Int): File =
        File(cacheDirectory, "surah_${number.toString().padStart(3, '0')}.json")

    /**
     * Migrate the existing four-entry text cache without treating it as a
     * substitute for all 114 complete chapter files.
     */
    private fun mergeLegacyCache(items: List<DhikrItem>): List<DhikrItem> {
        val legacy = readLegacyCachedItems()
        if (legacy.isEmpty()) return items
        return items.map { current ->
            if (current.arabic.isNotBlank() && current.transliteration.isNotBlank()) {
                current
            } else {
                legacy[current.id]?.takeIf { old ->
                    old.quranAyahKeys == current.quranAyahKeys &&
                        old.arabic.isNotBlank() &&
                        old.transliteration.isNotBlank() &&
                        old.meaning.isNotBlank()
                } ?: current
            }
        }
    }

    private fun readLegacyCachedItems(): Map<String, DhikrItem> = runCatching {
        val raw = preferences.getString("items", null) ?: return@runCatching emptyMap()
        if (raw.length > MAX_QURAN_CACHE_CHARS) return@runCatching emptyMap()
        val array = JSONArray(raw)
        if (array.length() !in 1..8) return@runCatching emptyMap()

        buildMap {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val expectedKeys = QuranicDuaCatalog.expectedAyahKeysForId(id) ?: continue
                val keys = item.optJSONArray("ayahKeys") ?: continue
                val storedKeys = (0 until keys.length()).map { keys.optString(it) }
                if (storedKeys != expectedKeys) continue

                val arabic = item.optString("arabic")
                val transliteration = item.optString("transliteration")
                val meaning = item.optString("meaning")
                if (arabic.isBlank() || transliteration.isBlank() || meaning.isBlank()) continue
                put(
                    id,
                    DhikrItem(
                        id = id,
                        value = item.optString("value"),
                        title = item.optString("title"),
                        category = "Quran",
                        arabic = arabic,
                        transliteration = transliteration,
                        meaning = meaning,
                        reference = item.optString("reference"),
                        quranAyahKeys = storedKeys,
                        categories = listOf("Quran")
                    )
                )
            }
        }
    }.getOrDefault(emptyMap())

    private fun fetchChapter(chapter: Int): QuranSurahText? {
        val address =
            "https://api.alquran.cloud/v1/surah/$chapter/editions/quran-uthmani,en.transliteration,en.sahih"
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            useCaches = false
            connectTimeout = 7_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "SereneDhikr/1.0")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val payload = readUtf8WithLimit(connection.inputStream, MAX_QURAN_SURAH_RESPONSE_BYTES)
            val editions = JSONObject(payload).optJSONArray("data") ?: return null
            if (editions.length() < 3) return null

            // Edition order is the same as the request above.
            val arabicAyahs = editions.optJSONObject(0)?.optJSONArray("ayahs") ?: return null
            val transliterationAyahs = editions.optJSONObject(1)?.optJSONArray("ayahs") ?: return null
            val translationAyahs = editions.optJSONObject(2)?.optJSONArray("ayahs") ?: return null
            val expectedCount = QuranRecitationAudio.ayahCountForSurah(chapter)
            if (expectedCount <= 0 || arabicAyahs.length() != expectedCount ||
                transliterationAyahs.length() != expectedCount ||
                translationAyahs.length() != expectedCount
            ) return null

            fun readEdition(ayahs: JSONArray): Map<Int, String> = buildMap {
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

            val arabic = readEdition(arabicAyahs)
            val transliteration = readEdition(transliterationAyahs)
            val translation = readEdition(translationAyahs)
            val verses = (1..expectedCount).map { number ->
                val arabicText = arabic[number].orEmpty()
                val transliterationText = transliteration[number].orEmpty()
                val translationText = translation[number].orEmpty()
                if (arabicText.isBlank() || transliterationText.isBlank() || translationText.isBlank()) {
                    return null
                }
                QuranAyahText(number, arabicText, transliterationText, translationText)
            }
            QuranSurahText(chapter, verses)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val QURAN_CACHE_SCHEMA_VERSION = 1
        const val MAX_QURAN_SURAH_CACHE_BYTES = 4 * 1024 * 1024
    }
}