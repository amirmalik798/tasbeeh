package com.amirmalik.tasbeeh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DhikrCatalogIntegrityTest {

    @Test
    fun dedicatedQuranLibraryContainsOnlyTheFourRequestedSelections() {
        assertEquals(4, QuranicDuaCatalog.additions.size)
        assertEquals(
            listOf(
                "Surah al-Fatihah",
                "4 Qul",
                "Ayat al-Kursi",
                "Last two ayahs of Surah al-Baqarah"
            ),
            QuranicDuaCatalog.additions.map { it.title }
        )
    }

    @Test
    fun allQuranSelectionsAreInTheDedicatedQuranCategory() {
        val items = QuranicDuaCatalog.fallbackItems()
        assertEquals(4, items.size)
        items.forEach { item ->
            assertEquals("Quran", item.category)
            assertEquals(listOf("Quran"), item.categories)
            assertTrue(item.isCountable)
            assertFalse(item.quranAyahKeys.isEmpty())
        }
    }

    @Test
    fun quranSelectionIdsValuesAndAyahMappingsAreUniqueAndValid() {
        val items = QuranicDuaCatalog.fallbackItems()
        assertEquals(items.size, items.map { it.id }.distinct().size)
        assertEquals(items.size, items.map { it.value }.distinct().size)
        items.forEach { item ->
            assertEquals(
                "Every ayah in ${item.id} must resolve to Quran Foundation",
                item.quranAyahKeys.size,
                QuranRecitationAudio.audioUrlsForAyahs(item.quranAyahKeys).size
            )
        }
    }

    @Test
    fun QuranTextIsOnlyReportedCompleteWhenEveryRequestedAyahIsPresent() {
        val fallback = QuranicDuaCatalog.fallbackItems()
        assertFalse(hasCompleteQuranicText(fallback))

        val complete = fallback.map { item -> item.copy(arabic = "نَصٌّ", transliteration = "Nass") }
        assertTrue(hasCompleteQuranicText(complete))

        val missingAyah = complete.mapIndexed { index, item ->
            if (index == 1) item.copy(quranAyahKeys = item.quranAyahKeys.dropLast(1)) else item
        }
        assertFalse(hasCompleteQuranicText(missingAyah))

        val missingArabic = complete.mapIndexed { index, item ->
            if (index == 2) item.copy(arabic = "") else item
        }
        assertFalse(hasCompleteQuranicText(missingArabic))
    }

    @Test
    fun QuranRefreshRequiresArabicTransliterationAndTranslationForEveryAyah() {
        val allVerses = QuranicDuaCatalog.additions
            .flatMap { it.ayahKeys }
            .associateWith { Triple("نَصٌّ", "Nass", "Translation") }
        assertTrue(hasAllQuranicTextAndTranslations(QuranicDuaCatalog.additions, allVerses))

        val missingArabic = allVerses.toMutableMap().apply {
            val key = QuranicDuaCatalog.additions.first().ayahKeys.first()
            put(key, Triple("", "Nass", "Translation"))
        }
        assertFalse(hasAllQuranicTextAndTranslations(QuranicDuaCatalog.additions, missingArabic))

        val missingTransliteration = allVerses.toMutableMap().apply {
            val key = QuranicDuaCatalog.additions.first().ayahKeys.first()
            put(key, Triple("نَصٌّ", "", "Translation"))
        }
        assertFalse(hasAllQuranicTextAndTranslations(QuranicDuaCatalog.additions, missingTransliteration))

        val missingTranslation = allVerses.toMutableMap().apply {
            val key = QuranicDuaCatalog.additions.first().ayahKeys.first()
            put(key, Triple("نَصٌّ", "Nass", ""))
        }
        assertFalse(hasAllQuranicTextAndTranslations(QuranicDuaCatalog.additions, missingTranslation))

        val missingVerse = allVerses.toMutableMap().apply {
            remove(QuranicDuaCatalog.additions.first().ayahKeys.first())
        }
        assertFalse(hasAllQuranicTextAndTranslations(QuranicDuaCatalog.additions, missingVerse))
    }

    @Test
    fun savedQuranTextMustProveEveryRequestedAyahWasCached() {
        val completeItems = QuranicDuaCatalog.fallbackItems().map { it.copy(arabic = "نَصٌّ", transliteration = "Nass") }
        val completeKeys = QuranicDuaCatalog.additions.associate { it.id to it.ayahKeys }
        assertTrue(hasCompleteQuranicCache(completeItems, completeKeys))

        val missingAyah = completeKeys.toMutableMap().apply {
            val definition = QuranicDuaCatalog.additions.first()
            put(definition.id, definition.ayahKeys.dropLast(1))
        }
        assertFalse(hasCompleteQuranicCache(completeItems, missingAyah))
        // A valid v2 cache written before per-ayah metadata must remain usable offline.
        assertTrue(hasCompleteQuranicCache(completeItems, emptyMap()))
    }

    @Test
    fun theFourQuranEntriesHaveTheExactRequestedSurahVerseRanges() {
        val items = QuranicDuaCatalog.additions.associateBy { it.id }
        assertEquals(
            (1..7).map { "1:$it" },
            items.getValue("quran-surah-al-fatiha").ayahKeys
        )
        assertEquals(
            listOf(
                "109:1", "109:2", "109:3", "109:4", "109:5", "109:6",
                "112:1", "112:2", "112:3", "112:4",
                "113:1", "113:2", "113:3", "113:4", "113:5",
                "114:1", "114:2", "114:3", "114:4", "114:5", "114:6"
            ),
            items.getValue("quran-four-qul").ayahKeys
        )
        assertEquals(listOf("2:255"), items.getValue("quran-ayat-al-kursi").ayahKeys)
        assertEquals(
            listOf("2:285", "2:286"),
            items.getValue("quran-last-two-baqarah").ayahKeys
        )
    }

    @Test
    fun categoryOrderIsFixedBeforeOpenDuaCategoriesAndKeepsApiOrder() {
        val apiCategories = listOf("Morning and evening", "Sleep and waking", "Travel")
        assertEquals(
            listOf("All", "Quran", "My Dhikhrs", "Morning and evening", "Sleep and waking", "Travel"),
            orderedDhikrCategories(apiCategories)
        )
    }
}
