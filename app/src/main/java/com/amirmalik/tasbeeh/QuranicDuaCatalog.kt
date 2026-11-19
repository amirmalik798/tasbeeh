package com.amirmalik.tasbeeh

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal data class QuranicDuaDefinition(
    val id: String,
    val title: String,
    val category: String,
    val reference: String,
    val ayahKeys: List<String>,
    val context: String
)

/**
 * Exactly 63 additions to the 37 Qur'anic items already in the built-in
 * catalogue, giving the app 100 Qur'anic supplications/passages in total.
 * Text and translations are read from Quran Foundation; each item carries
 * explicit ayah keys so its audio can only resolve to Quran Foundation.
 */
internal object QuranicDuaCatalog {
    val additions: List<QuranicDuaDefinition> = listOf(
        // These are deliberately the first three Sleep entries.
        q("sleep-four-qul", "The Four Quls", "Sleep",
            "Qur'an 109:1–6 · 112:1–4 · 113:1–5 · 114:1–6",
            keys("109:1", "109:2", "109:3", "109:4", "109:5", "109:6",
                "112:1", "112:2", "112:3", "112:4",
                "113:1", "113:2", "113:3", "113:4", "113:5",
                "114:1", "114:2", "114:3", "114:4", "114:5", "114:6"),
            "The four short protective surahs: al-Kafirun, al-Ikhlas, al-Falaq and an-Nas."),
        q("sleep-ayat-al-kursi", "Ayat al-Kursi", "Sleep",
            "Qur'an 2:255", keys("2:255"),
            "The Throne Verse. The complete ayah is streamed from Quran Foundation."),
        q("sleep-last-two-baqarah", "Last Two Ayahs of Surah al-Baqarah", "Sleep",
            "Qur'an 2:285–286", keys("2:285", "2:286"),
            "The final two ayahs of al-Baqarah. Both complete ayahs are streamed in order."),

        q("quran-2-67", "Seeking refuge from ignorance", "Guidance & Knowledge",
            "Qur'an 2:67", keys("2:67"),
            "A reported plea seeking Allah's protection from acting ignorantly."),
        q("quran-2-126", "Ibrahim's prayer for a secure city", "Family & Community",
            "Qur'an 2:126", keys("2:126"),
            "A prayer for safety and provision for the people of a city."),
        q("quran-al-fatiha", "Surah al-Fatihah: prayer for guidance", "Guidance & Knowledge",
            "Qur'an 1:1–7", keys("1:1", "1:2", "1:3", "1:4", "1:5", "1:6", "1:7"),
            "The opening surah includes the central request to be guided to the straight path."),
        q("quran-46-15", "Gratitude and righteousness for one's family", "Family & Community",
            "Qur'an 46:15", keys("46:15"),
            "A prayer to be grateful, to do righteous deeds and to seek righteousness for one's descendants."),
        q("quran-29-30", "Seeking help against corruption", "Protection & Distress",
            "Qur'an 29:30", keys("29:30"),
            "A prophet's context-specific plea for help against a corrupt people."),
        q("quran-7-151", "Musa's prayer for forgiveness and mercy", "Forgiveness",
            "Qur'an 7:151", keys("7:151"),
            "Musa asks forgiveness for himself and his brother and for inclusion in Allah's mercy."),
        q("quran-7-155", "Seeking forgiveness and mercy", "Forgiveness",
            "Qur'an 7:155", keys("7:155"),
            "A prayer acknowledging Allah as the protecting guardian and asking for forgiveness and mercy."),
        q("quran-7-156", "Good in this life and the Hereafter", "Wellbeing & Hereafter",
            "Qur'an 7:156", keys("7:156"),
            "A plea for goodness in this world and the Hereafter."),
        q("quran-25-65", "Protection from the punishment of Hell", "Wellbeing & Hereafter",
            "Qur'an 25:65", keys("25:65"),
            "The servants of the Most Merciful ask to be kept away from Hell's punishment."),
        q("quran-59-10", "Forgiveness for the believing community", "Forgiveness",
            "Qur'an 59:10", keys("59:10"),
            "A prayer for forgiveness for oneself and earlier believers, and for hearts free of resentment."),
        q("quran-17-24", "Mercy for one's parents", "Family & Community",
            "Qur'an 17:24", keys("17:24"),
            "A direct prayer asking Allah to show mercy to one's parents."),
        q("quran-17-80", "A truthful entrance and a truthful exit", "Guidance & Knowledge",
            "Qur'an 17:80", keys("17:80"),
            "A prayer for a truthful beginning, a truthful ending and supporting authority from Allah."),
        q("quran-5-83", "Faith and being counted among the witnesses", "Faith & Steadfastness",
            "Qur'an 5:83", keys("5:83"),
            "A response of faith asking to be counted among those who bear witness to the truth."),
        q("quran-5-114", "A prayer for provision and a sign", "Provision & Gratitude",
            "Qur'an 5:114", keys("5:114"),
            "Isa asks Allah for a table from heaven, provision and a sign."),
        q("quran-40-7", "The angels' prayer for the repentant", "Forgiveness",
            "Qur'an 40:7", keys("40:7"),
            "The angels ask forgiveness for those who repent and follow Allah's way."),
        q("quran-40-8", "A prayer for the righteous and their families", "Family & Community",
            "Qur'an 40:8", keys("40:8"),
            "The angels ask that believers and their righteous families be admitted to the promised gardens."),
        q("quran-23-29", "A blessed landing", "Travel",
            "Qur'an 23:29", keys("23:29"),
            "A prayer asking Allah for a blessed place to land."),
        q("quran-23-97-98", "Protection from the whispers of devils", "Protection & Distress",
            "Qur'an 23:97–98", keys("23:97", "23:98"),
            "A prayer seeking refuge from the incitements and presence of devils."),
        q("quran-60-4", "Trust and return to Allah", "Faith & Steadfastness",
            "Qur'an 60:4", keys("60:4"),
            "A declaration of reliance upon Allah, repentance to Him and return to Him."),
        q("quran-60-5", "Protection from being a trial for disbelievers", "Protection & Distress",
            "Qur'an 60:5", keys("60:5"),
            "A prayer for protection from becoming a trial for those who reject faith, and for forgiveness."),
        q("quran-54-10", "A plea for help when overwhelmed", "Protection & Distress",
            "Qur'an 54:10", keys("54:10"),
            "Nuh calls upon Allah for help when overwhelmed by opposition."),
        q("quran-28-16", "Acknowledging a wrong and asking forgiveness", "Forgiveness",
            "Qur'an 28:16", keys("28:16"),
            "Musa acknowledges his mistake and asks Allah to forgive him."),
        q("quran-4-75", "A prayer for oppressed believers", "Protection & Distress",
            "Qur'an 4:75", keys("4:75"),
            "A plea for rescue and a protector for oppressed men, women and children."),
        q("quran-37-100", "A prayer for righteous offspring", "Family & Community",
            "Qur'an 37:100", keys("37:100"),
            "Ibrahim asks Allah to grant him a child among the righteous."),
        q("quran-26-83-85", "Wisdom and companionship with the righteous", "Guidance & Knowledge",
            "Qur'an 26:83–85", keys("26:83", "26:84", "26:85"),
            "Ibrahim asks for wisdom, a good legacy and to be among the inheritors of Paradise."),
        q("quran-66-8", "Perfect our light and forgive us", "Forgiveness",
            "Qur'an 66:8", keys("66:8"),
            "The believers ask Allah to perfect their light and forgive them."),
        q("quran-11-41", "Beginning a journey in Allah's name", "Travel",
            "Qur'an 11:41", keys("11:41"),
            "Nuh calls upon Allah's name as the Ark sets sail and comes to rest."),
        q("quran-11-47", "Seeking forgiveness and mercy after a mistake", "Forgiveness",
            "Qur'an 11:47", keys("11:47"),
            "Nuh seeks Allah's protection from asking without knowledge and asks for forgiveness and mercy."),
        q("quran-14-38", "Allah knows what is hidden and what is declared", "Faith & Steadfastness",
            "Qur'an 14:38", keys("14:38"),
            "Ibrahim affirms that nothing is hidden from Allah."),
        q("quran-14-40", "Establishing prayer and asking for acceptance", "Prayer",
            "Qur'an 14:40", keys("14:40"),
            "Ibrahim asks to establish prayer and asks Allah to accept his supplication."),
        q("quran-14-41", "Forgiveness for parents and believers", "Forgiveness",
            "Qur'an 14:41", keys("14:41"),
            "Ibrahim asks forgiveness for himself, his parents and the believers on the Day of Reckoning."),
        q("quran-19-5", "A prayer for an heir", "Family & Community",
            "Qur'an 19:5", keys("19:5"),
            "Zakariya asks Allah for an heir."),
        q("quran-71-28", "Forgiveness for oneself, parents and believers", "Forgiveness",
            "Qur'an 71:28", keys("71:28"),
            "Nuh asks forgiveness for himself, his parents, those who enter his home as believers and believing men and women."),
        q("quran-38-35", "Seeking forgiveness and a kingdom", "Forgiveness",
            "Qur'an 38:35", keys("38:35"),
            "Sulayman asks forgiveness and a kingdom, acknowledging Allah as the Bestower."),
        q("quran-10-22", "Calling upon Allah in danger at sea", "Travel & Distress",
            "Qur'an 10:22", keys("10:22"),
            "A passage describing people calling upon Allah sincerely when danger strikes at sea."),
        q("quran-10-85-86", "A prayer for protection from persecution", "Protection & Distress",
            "Qur'an 10:85–86", keys("10:85", "10:86"),
            "Believers ask Allah not to make them a trial for wrongdoing people and to save them by His mercy."),
        q("quran-12-23", "Seeking refuge from wrongdoing", "Protection & Distress",
            "Qur'an 12:23", keys("12:23"),
            "Yusuf seeks refuge in Allah from wrongdoing and betrayal of trust."),
        q("quran-12-33", "Seeking Allah's protection from temptation", "Protection & Distress",
            "Qur'an 12:33", keys("12:33"),
            "Yusuf asks Allah to protect him from temptation and from acting ignorantly."),
        q("quran-12-101", "A good ending in submission to Allah", "Wellbeing & Hereafter",
            "Qur'an 12:101", keys("12:101"),
            "Yusuf asks to die in submission and to be joined with the righteous."),
        q("quran-2-129", "Ibrahim's prayer for a messenger and teaching", "Guidance & Knowledge",
            "Qur'an 2:129", keys("2:129"),
            "Ibrahim and Ismail ask for a messenger from among their descendants who will recite, teach and purify."),
        q("quran-2-260", "Ibrahim's request for reassurance", "Faith & Steadfastness",
            "Qur'an 2:260", keys("2:260"),
            "Ibrahim asks to be shown how Allah gives life to the dead; the passage explains his request for reassurance."),
        q("quran-3-26-27", "Allah is the Owner of all sovereignty", "Faith & Steadfastness",
            "Qur'an 3:26–27", keys("3:26", "3:27"),
            "A Qur'anic invocation affirming Allah's sovereignty and power to give and take dominion."),
        q("quran-5-25", "Musa asks Allah to judge between him and his people", "Protection & Distress",
            "Qur'an 5:25", keys("5:25"),
            "Musa asks Allah to separate him and his brother from a persistently disobedient people."),
        q("quran-7-143", "Musa's request to behold Allah", "Faith & Steadfastness",
            "Qur'an 7:143", keys("7:143"),
            "A context-specific request made by Musa; read with the surrounding account."),
        q("quran-10-88", "Musa's prayer concerning Pharaoh's oppression", "Protection & Distress",
            "Qur'an 10:88", keys("10:88"),
            "Musa asks Allah to remove Pharaoh's power and prevent it from leading people astray; a context-specific prophetic prayer."),
        q("quran-14-37", "Ibrahim's prayer for his family near the Sacred House", "Family & Community",
            "Qur'an 14:37", keys("14:37"),
            "Ibrahim asks Allah to help his descendants establish prayer, to turn hearts toward them and provide for them."),
        q("quran-19-4-6", "Zakariya's prayer for a righteous successor", "Family & Community",
            "Qur'an 19:4–6", keys("19:4", "19:5", "19:6"),
            "Zakariya explains his need and asks Allah for a successor who will be pleasing to Him."),
        q("quran-20-45", "Musa and Harun ask for protection", "Protection & Distress",
            "Qur'an 20:45", keys("20:45"),
            "Musa and Harun ask Allah for protection from being overpowered or transgressed against."),
        q("quran-26-169", "A prayer for rescue from wrongdoing", "Protection & Distress",
            "Qur'an 26:169", keys("26:169"),
            "Lut asks Allah to rescue him and his family from the deeds of his people."),
        q("quran-39-46", "Asking Allah to judge with truth", "Guidance & Knowledge",
            "Qur'an 39:46", keys("39:46"),
            "The Prophet is instructed to call upon Allah, Knower of the unseen and seen, to judge between His servants."),
        q("quran-40-27", "Musa seeks refuge from arrogance", "Protection & Distress",
            "Qur'an 40:27", keys("40:27"),
            "Musa seeks refuge in Allah from every arrogant person who does not believe in the Day of Account."),
        q("quran-40-44", "Entrusting one's affairs to Allah", "Faith & Steadfastness",
            "Qur'an 40:44", keys("40:44"),
            "A believer entrusts his affairs to Allah, affirming that Allah sees His servants."),
        q("quran-43-13-14", "The Qur'anic remembrance when mounting a ride", "Travel",
            "Qur'an 43:13–14", keys("43:13", "43:14"),
            "A Qur'anic remembrance praising the One who made transport available and acknowledging the return to Allah."),
        q("quran-66-11", "Asiyah's prayer for a home in Paradise", "Wellbeing & Hereafter",
            "Qur'an 66:11", keys("66:11"),
            "Asiyah asks Allah for a home near Him in Paradise and for rescue from Pharaoh and wrongdoing people."),
        q("quran-11-45", "Nuh's plea concerning his son", "Family & Community",
            "Qur'an 11:45", keys("11:45"),
            "Nuh makes a plea concerning his son; read the full exchange and its response in context."),
        q("quran-21-112", "A plea for judgment with truth", "Guidance & Knowledge",
            "Qur'an 21:112", keys("21:112"),
            "The Prophet asks Allah to judge with truth and invokes Allah as the Most Merciful."),
        q("quran-23-26", "Nuh's plea for help", "Protection & Distress",
            "Qur'an 23:26", keys("23:26"),
            "Nuh asks Allah for help after his people reject him."),
        q("quran-23-39", "A prophet's plea for help after denial", "Protection & Distress",
            "Qur'an 23:39", keys("23:39"),
            "A prophet asks Allah for help after being denied by his people."),
        q("quran-23-93-94", "Seeking protection from unjust people", "Protection & Distress",
            "Qur'an 23:93–94", keys("23:93", "23:94"),
            "A prayer asking Allah not to place the supplicant among wrongdoing people."),
        q("quran-26-117-118", "Nuh's prayer for a just decision and rescue", "Protection & Distress",
            "Qur'an 26:117–118", keys("26:117", "26:118"),
            "Nuh asks Allah for a just decision between him and his people and for rescue for the believers with him.")
    )

    init {
        require(additions.size == 63) {
            "The additional Qur'anic catalogue must contain exactly 63 entries."
        }
        require(additions.map { it.id }.distinct().size == additions.size) {
            "Qur'anic dua IDs must be unique."
        }
        require(additions.all { it.ayahKeys.isNotEmpty() && it.ayahKeys.all { key -> QuranRecitationAudio.audioUrlForAyah(key) != null } }) {
            "Every Qur'anic dua must resolve to Quran Foundation audio."
        }
    }

    fun fallbackItems(): List<DhikrItem> = additions.map { definition ->
        DhikrItem(
            id = definition.id,
            value = definition.title,
            title = definition.title,
            category = definition.category,
            arabic = "",
            transliteration = "",
            meaning = definition.context,
            reference = definition.reference,
            quranAyahKeys = definition.ayahKeys
        )
    }

    private fun q(
        id: String,
        title: String,
        category: String,
        reference: String,
        ayahKeys: List<String>,
        context: String
    ) = QuranicDuaDefinition(id, title, category, reference, ayahKeys, context)

    private fun keys(vararg values: String) = values.toList()
}

internal class QuranicDuaRepository(context: Context) {
    private val preferences = context.getSharedPreferences("quranic_dua_catalog", Context.MODE_PRIVATE)

    fun loadCachedItems(): List<DhikrItem> {
        val raw = preferences.getString("items_v1", null) ?: return QuranicDuaCatalog.fallbackItems()
        return runCatching {
            val array = JSONArray(raw)
            if (array.length() != 63) return@runCatching QuranicDuaCatalog.fallbackItems()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val keys = item.optJSONArray("ayahKeys") ?: JSONArray()
                    add(
                        DhikrItem(
                            id = item.optString("id"),
                            value = item.optString("value"),
                            title = item.optString("title"),
                            category = item.optString("category"),
                            arabic = item.optString("arabic"),
                            transliteration = "",
                            meaning = item.optString("meaning"),
                            reference = item.optString("reference"),
                            quranAyahKeys = (0 until keys.length()).map { keys.optString(it) }
                        )
                    )
                }
            }.takeIf { it.size == 63 } ?: QuranicDuaCatalog.fallbackItems()
        }.getOrDefault(QuranicDuaCatalog.fallbackItems())
    }

    suspend fun refresh(): List<DhikrItem> = withContext(Dispatchers.IO) {
        val chapters = QuranicDuaCatalog.additions
            .flatMap { it.ayahKeys }
            .mapNotNull { it.substringBefore(':').toIntOrNull() }
            .distinct()
            .sorted()
        val verseByKey = mutableMapOf<String, Pair<String, String>>()

        chapters.chunked(5).forEach { batch ->
            val responses = coroutineScope {
                batch.map { chapter -> async { fetchChapter(chapter) } }.awaitAll()
            }
            responses.filterNotNull().forEach { response ->
                response.forEach { (key, content) -> verseByKey[key] = content }
            }
        }

        val items = QuranicDuaCatalog.additions.map { definition ->
            val verses = definition.ayahKeys.mapNotNull { verseByKey[it] }
            val arabic = verses.map { it.first }.filter { it.isNotBlank() }.joinToString(" ۞ ")
            val translation = verses.map { it.second }.filter { it.isNotBlank() }.joinToString(" ")
            DhikrItem(
                id = definition.id,
                value = definition.title,
                title = definition.title,
                category = definition.category,
                arabic = arabic,
                transliteration = "",
                meaning = translation.ifBlank { definition.context },
                reference = definition.reference,
                quranAyahKeys = definition.ayahKeys
            )
        }

        val cachedItems = loadCachedItems()
        val hasCompleteArabicText = items.all { it.arabic.isNotBlank() }
        if (hasCompleteArabicText) {
            runCatching {
                val array = JSONArray()
                items.forEach { item ->
                    array.put(JSONObject().apply {
                        put("id", item.id)
                        put("value", item.value)
                        put("title", item.title)
                        put("category", item.category)
                        put("arabic", item.arabic)
                        put("meaning", item.meaning)
                        put("reference", item.reference)
                        put("ayahKeys", JSONArray(item.quranAyahKeys))
                    })
                }
                preferences.edit().putString("items_v1", array.toString()).apply()
            }
        }
        if (hasCompleteArabicText) items else cachedItems.takeIf {
            it.size == 63 && it.any { cached -> cached.arabic.isNotBlank() }
        } ?: items
    }

    private fun fetchChapter(chapter: Int): Map<String, Pair<String, String>>? {
        // AlQuran Cloud exposes Uthmani Arabic and the Sahih International
        // translation through a public read API. Quran Foundation's current
        // Content API requires app credentials, which must not be embedded in
        // a mobile client. Qur'anic audio still comes exclusively from Quran Foundation.
        val address = "https://api.alquran.cloud/v1/surah/" + chapter +
            "/editions/quran-uthmani,en.sahih"
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NOOR-E-DHIKR/1.0")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val editions = JSONObject(payload).optJSONArray("data") ?: return null
            if (editions.length() < 2) return null
            val arabicAyahs = editions.optJSONObject(0)?.optJSONArray("ayahs") ?: return null
            val translationAyahs = editions.optJSONObject(1)?.optJSONArray("ayahs") ?: return null
            val translationsByNumber = buildMap<Int, String> {
                for (index in 0 until translationAyahs.length()) {
                    val ayah = translationAyahs.optJSONObject(index) ?: continue
                    val number = ayah.optInt("numberInSurah", -1)
                    val text = ayah.optString("text").trim()
                        .replace(Regex("<[^>]*>"), " ")
                        .replace(Regex("\\\\s+"), " ")
                        .trim()
                    if (number > 0 && text.isNotBlank()) put(number, text)
                }
            }
            buildMap {
                for (index in 0 until arabicAyahs.length()) {
                    val ayah = arabicAyahs.optJSONObject(index) ?: continue
                    val number = ayah.optInt("numberInSurah", -1)
                    val arabic = ayah.optString("text").trim()
                    val translation = translationsByNumber[number].orEmpty()
                    if (number > 0 && arabic.isNotBlank()) {
                        put("$chapter:$number", arabic to translation)
                    }
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
