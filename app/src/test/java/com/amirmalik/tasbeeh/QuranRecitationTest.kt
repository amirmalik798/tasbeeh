package com.amirmalik.tasbeeh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranRecitationTest {

    @Test
    fun openingBasmalaAppearsForEverySurahExceptAtTawbah() {
        assertTrue(QuranBasmala.shouldShowForSurah(1))
        assertTrue(QuranBasmala.shouldShowForSurah(2))
        assertTrue(QuranBasmala.shouldShowForSurah(114))
        assertFalse(QuranBasmala.shouldShowForSurah(9))
        assertFalse(QuranBasmala.shouldShowForSurah(null))
        assertFalse(QuranBasmala.shouldShowForSurah(0))
    }

    @Test
    fun basmalaIsStrippedFromTheFirstAyahWhenItIsPrefixedToTheAyahText() {
        val firstAyah = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ"
        assertEquals(
            "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
            QuranBasmala.stripLeadingPrefix(firstAyah)
        )
    }

    @Test
    fun standaloneBasmalaAyahCanBeMovedIntoTheSeparateOpeningBlockWithoutDuplication() {
        assertTrue(QuranBasmala.isStandaloneBasmala(QuranBasmala.ARABIC_TEXT))
        assertTrue(QuranBasmala.isStandaloneBasmala("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"))
        assertFalse(QuranBasmala.isStandaloneBasmala("الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ"))
    }

    @Test
    fun basmalaRemovalDoesNotChangeAnAyahThatDoesNotStartWithIt() {
        val firstAyah = "الٓمٓ"
        assertEquals(firstAyah, QuranBasmala.stripLeadingPrefix(firstAyah))
    }

    @Test
    fun quranFoundationReciterCatalogueKeepsDistinctRecitationStylesAndStableIds() {
        val payload = """
            {
              "recitations": [
                {
                  "id": 1,
                  "reciter_name": "AbdulBaset AbdulSamad",
                  "style": "Mujawwad",
                  "translated_name": {"name": "AbdulBaset AbdulSamad", "language_name": "english"}
                },
                {
                  "id": 2,
                  "reciter_name": "AbdulBaset AbdulSamad",
                  "style": "Murattal",
                  "translated_name": {"name": "AbdulBaset AbdulSamad", "language_name": "english"}
                },
                {
                  "id": 3,
                  "reciter_name": "Abdur-Rahman as-Sudais",
                  "style": null,
                  "translated_name": {"name": "Abdur-Rahman as-Sudais", "language_name": "english"}
                }
              ]
            }
        """.trimIndent()

        val reciters = QuranReciterCatalog.parse(payload)

        assertEquals(listOf(1, 2, 3), reciters.map { it.id })
        assertEquals("AbdulBaset AbdulSamad · Mujawwad", reciters[0].displayName)
        assertEquals("AbdulBaset AbdulSamad · Murattal", reciters[1].displayName)
        assertEquals("Abdur-Rahman as-Sudais", reciters[2].displayName)
        assertEquals(reciters, QuranReciterCatalog.decode(QuranReciterCatalog.encode(reciters)))
    }

    @Test
    fun offlineReciterCatalogueContainsThePublishedAyahByAyahOptions() {
        val reciters = QuranReciterCatalog.fallback

        assertEquals(12, reciters.size)
        assertEquals(reciters.size, reciters.map { it.id }.distinct().size)
        assertEquals(QuranReciterCatalog.DEFAULT_RECITER_ID, reciters.first { it.id == 1 }.id)
        assertTrue(reciters.any { it.id == 2 && it.style == "Murattal" })
        assertTrue(reciters.any { it.id == 12 && it.style == "Muallim" })
    }

    @Test
    fun onlyThe114SurahsAppearInTheDedicatedQuranCategory() {
        val items = QuranicDuaCatalog.fallbackItems()
        val quranCategory = items.filter(::isQuranCategorySurah)

        assertEquals(114, quranCategory.size)
        assertTrue(quranCategory.all(QuranicDuaCatalog::isFullSurah))
        assertFalse(quranCategory.any { it.id == "quran-four-qul" })
        assertFalse(quranCategory.any { it.id == "quran-ayat-al-kursi" })
        assertFalse(quranCategory.any { it.id == "quran-last-two-baqarah" })
    }

    @Test
    fun ayahKeysResolveToDocumentedQuranFoundationAudioPaths() {
        assertEquals(
            "https://verses.quran.foundation/AbdulBaset/Mujawwad/mp3/002255.mp3",
            QuranRecitationAudio.audioUrlForAyah("2:255")
        )
        assertEquals(
            "https://verses.quran.foundation/AbdulBaset/Mujawwad/mp3/114006.mp3",
            QuranRecitationAudio.audioUrlForAyah("114:6")
        )
    }

    @Test
    fun invalidAyahKeysNeverProduceNetworkUrls() {
        listOf("", "2", "2:0", "0:1", "115:1", "2:287", "abc:1", "2:1:4").forEach { key ->
            assertNull("Invalid key should be rejected: $key", QuranRecitationAudio.audioUrlForAyah(key))
        }
    }

    @Test
    fun multipleAyahsPreserveOrderAndDoNotDuplicateUrls() {
        val urls = QuranRecitationAudio.audioUrlsForAyahs(listOf("20:25", "20:26", "20:25"))
        assertEquals(
            listOf(
                QuranRecitationAudio.audioUrlForAyah("20:25"),
                QuranRecitationAudio.audioUrlForAyah("20:26")
            ),
            urls
        )
    }

    @Test
    fun completeQuranCatalogueContainsAll114SurahsAndRetainsQuickSelections() {
        val items = QuranicDuaCatalog.fallbackItems()
        val surahs = items.filter(QuranicDuaCatalog::isFullSurah)

        assertEquals(117, items.size) // 114 complete surahs + 3 established quick selections.
        assertEquals(114, QuranicDuaCatalog.surahs.size)
        assertEquals(114, surahs.size)
        assertEquals(6236, QuranicDuaCatalog.surahs.sumOf { it.ayahKeys.size })
        assertEquals("Surah al-Fatihah", surahs.first().title)
        assertEquals("quran-surah-al-fatiha", surahs.first().id)
        assertEquals(7, surahs.first().quranAyahKeys.size)
        assertEquals(286, surahs[1].quranAyahKeys.size)
        assertTrue(surahs.map { it.id }.distinct().size == 114)
        assertTrue(surahs.zipWithNext().all { (left, right) ->
            right.quranAyahKeys.first().substringBefore(':').toInt() ==
                left.quranAyahKeys.first().substringBefore(':').toInt() + 1
        })

        assertTrue(items.any { it.id == "quran-four-qul" })
        assertTrue(items.any { it.id == "quran-ayat-al-kursi" })
        assertTrue(items.any { it.id == "quran-last-two-baqarah" })

        items.forEach { item ->
            assertEquals("Quran", item.category)
            assertFalse("Missing ayah keys for ${item.id}", item.quranAyahKeys.isEmpty())
            assertEquals(
                "Audio URL count should match ayah mapping for ${item.id}",
                item.quranAyahKeys.size,
                QuranRecitationAudio.audioUrlsForAyahs(item.quranAyahKeys).size
            )
            assertTrue(item.recitationAudioUrls.isEmpty())
        }
    }

    @Test
    fun everySurahAyahKeyMapsToAnOrderedTrustedRecitationUrl() {
        QuranicDuaCatalog.surahs.forEach { surah ->
            assertEquals(
                QuranRecitationAudio.ayahCountForSurah(surah.number),
                surah.ayahKeys.size
            )
            assertEquals(
                surah.ayahKeys.size,
                QuranRecitationAudio.audioUrlsForAyahs(surah.ayahKeys).size
            )
            assertEquals(
                "https://verses.quran.foundation/AbdulBaset/Mujawwad/mp3/" +
                    surah.number.toString().padStart(3, '0') + "001.mp3",
                QuranRecitationAudio.audioUrlForAyah(surah.ayahKeys.first())
            )
        }
    }

    @Test
    fun invalidSurahNumbersCannotCreateAyahKeysOrAudioUrls() {
        listOf(-1, 0, 115, 999).forEach { surah ->
            assertEquals(0, QuranRecitationAudio.ayahCountForSurah(surah))
            assertTrue(QuranRecitationAudio.ayahKeysForSurah(surah).isEmpty())
        }
    }

    @Test
    fun partialChapterTextIsNeverMarkedComplete() {
        val surah = QuranicDuaCatalog.surahs.first()
        val partial = (1..6).associate { number ->
            "${surah.number}:$number" to QuranAyahText(number, "Arabic", "Transliteration", "Translation")
        }
        assertFalse(hasAllSurahAyahTextAndTranslations(listOf(surah), partial))

        val complete = surah.ayahKeys.associate { key ->
            key to QuranAyahText(
                number = key.substringAfter(':').toInt(),
                arabic = "Arabic",
                transliteration = "Transliteration",
                translation = "Translation"
            )
        }
        assertTrue(hasAllSurahAyahTextAndTranslations(listOf(surah), complete))
    }

    @Test
    fun fullSurahTextIsNotCopiedUnboundedIntoSessionDataStoreSnapshots() {
        val longText = "Qur'anic text ".repeat(100)
        assertEquals(240, quranSessionSnapshotText(longText, isFullSurah = true).length)
        assertEquals(longText, quranSessionSnapshotText(longText, isFullSurah = false))
    }

    @Test
    fun fullSurahTextIsJoinedAndKeptAyahAlignedAcrossAllTextEditions() {
        val surah = QuranicDuaCatalog.surahs.first()
        val verses = surah.ayahKeys.associate { key ->
            val number = key.substringAfter(':').toInt()
            key to QuranAyahText(number, "Arabic $number", "Transliteration $number", "Translation $number")
        }
        val items = QuranicDuaCatalog.itemsFromSurahTexts(
            mapOf(1 to QuranSurahText(1, verses.values.sortedBy { it.number }))
        )
        val fatiha = items.first { it.id == surah.id }
        assertEquals(7, fatiha.quranAyahKeys.size)
        assertEquals(7, fatiha.arabic.split(QuranicDuaCatalog.VERSE_SEPARATOR).size)
        assertEquals(7, fatiha.transliteration.split(QuranicDuaCatalog.VERSE_SEPARATOR).size)
        assertEquals(7, fatiha.meaning.split(QuranicDuaCatalog.VERSE_SEPARATOR).size)
        assertTrue(fatiha.arabic.startsWith("Arabic 1"))
        assertTrue(fatiha.transliteration.endsWith("Transliteration 7"))
        assertTrue(fatiha.meaning.contains("Translation 7"))
    }

    @Test
    fun ordinaryOpenDuaAndCustomEntriesNeverGetRoutedToQuranFoundation() {
        val ordinaryEntry = DhikrItem(
            id = "opendua-entry-example",
            value = "An OpenDua entry",
            title = "An OpenDua entry",
            category = "Morning and evening",
            arabic = "اللَّهُمَّ",
            transliteration = "",
            meaning = "",
            recitationAudioUrls = listOf("https://audio.opendua.org/hisn/example.mp3")
        )
        assertTrue(ordinaryEntry.quranAyahKeys.isEmpty())
        assertFalse(QuranRecitationAudio.shouldUseQuranFoundation(ordinaryEntry.quranAyahKeys))

        val customItem = DhikrItem(
            id = "custom-user-entry",
            value = "My own dua",
            title = "My own dua",
            category = "My Dhikhrs",
            arabic = "اللَّهُمَّ",
            transliteration = "",
            meaning = "",
            customId = "custom-id",
            categories = listOf("My Dhikhrs"),
            contentType = "custom",
            isCountable = true
        )
        assertTrue(customItem.quranAyahKeys.isEmpty())
        assertFalse(QuranRecitationAudio.shouldUseQuranFoundation(customItem.quranAyahKeys))
    }

    @Test
    fun onlyEntriesWithExplicitValidAyahKeysUseQuranFoundation() {
        assertTrue(QuranRecitationAudio.shouldUseQuranFoundation(listOf("2:255")))
        assertFalse(QuranRecitationAudio.shouldUseQuranFoundation(emptyList()))
        assertFalse(QuranRecitationAudio.shouldUseQuranFoundation(listOf("2:999")))
        assertFalse(QuranRecitationAudio.shouldUseQuranFoundation(listOf("not-an-ayah")))
    }

    @Test
    fun anyExplicitAyahSelectionUsesQuranAudioPathEvenWhenItsMappingIsInvalid() {
        assertTrue(QuranRecitationAudio.shouldAttemptQuranFoundation(listOf("2:255")))
        assertTrue(QuranRecitationAudio.shouldAttemptQuranFoundation(listOf("2:999")))
        assertFalse(QuranRecitationAudio.shouldUseQuranFoundation(listOf("2:999")))
        assertNull(QuranRecitationAudio.audioUrlForAyah("2:287"))
        assertFalse(QuranRecitationAudio.shouldAttemptQuranFoundation(emptyList()))
    }

    @Test
    fun mediaPlayerIoFailureExplainsTheStreamReadProblemAndShowsDiagnosticCodes() {
        val message = recitationPlaybackFailureMessage(
            mediaPlayerWhat = android.media.MediaPlayer.MEDIA_ERROR_IO,
            mediaPlayerExtra = -2147483648
        )
        assertTrue(message.contains("could not read", ignoreCase = true))
        assertTrue(message.contains("MediaPlayer code: -1004"))
        assertTrue(message.contains("detail: -2147483648"))
    }

    @Test
    fun unsupportedAudioFormatHasAnActionableExplanation() {
        val message = recitationPlaybackFailureMessage(
            mediaPlayerWhat = android.media.MediaPlayer.MEDIA_ERROR_UNSUPPORTED
        )
        assertTrue(message.contains("not supported", ignoreCase = true))
    }

    @Test
    fun unknownHostFailureExplainsDnsOrConnectivityCause() {
        val message = recitationPlaybackFailureMessage(java.net.UnknownHostException("verses.quran.foundation"))
        assertTrue(message.contains("DNS", ignoreCase = true))
        assertTrue(message.contains("recitation provider"))
    }

    @Test
    fun timeoutFailureExplainsThatTheAudioServerDidNotRespond() {
        val message = recitationPlaybackFailureMessage(java.net.SocketTimeoutException("timeout"))
        assertTrue(message.contains("timed out", ignoreCase = true))
    }

    @Test
    fun missingAudioFileReportsThatTheRequestedRecitationWasNotFound() {
        val message = recitationPlaybackFailureMessage(java.io.FileNotFoundException("404"))
        assertTrue(message.contains("not found", ignoreCase = true))
    }
}
