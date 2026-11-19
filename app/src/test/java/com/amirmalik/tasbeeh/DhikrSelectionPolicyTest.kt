package com.amirmalik.tasbeeh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DhikrSelectionPolicyTest {

    @Test
    fun libraryCategoriesOmitAllAndKeepPinnedCategoriesBeforeOpenDuaTopics() {
        assertEquals(
            listOf("Favorites", "My Dhikhrs", "Quran", "Morning and Evening", "Travel"),
            orderedDhikrCategories(
                listOf("Quran", "Morning and Evening", "Favorites", "Travel", "My Dhikhrs", "All")
            )
        )
    }

    @Test
    fun dynamicOpenDuaTopicsCannotReintroduceTheRemovedAllCategory() {
        assertFalse(orderedDhikrCategories(listOf("All", "Travel")).contains("All"))
    }

    @Test
    fun blankSelectionCanBeRecoveredWhenNoSelectionOrSessionExists() {
        assertTrue(
            shouldRepairMissingDhikrSelection(
                storedDhikr = "",
                hasValidSelection = false,
                hasOpenSession = false
            )
        )
    }

    @Test
    fun unavailableCatalogueEntryDoesNotOverwriteANonblankSavedSelection() {
        assertFalse(
            shouldRepairMissingDhikrSelection(
                storedDhikr = "Previously selected OpenDua entry",
                hasValidSelection = false,
                hasOpenSession = false
            )
        )
    }

    @Test
    fun validSelectionIsNeverReplaced() {
        assertFalse(
            shouldRepairMissingDhikrSelection(
                storedDhikr = "Surah al-Fatihah",
                hasValidSelection = true,
                hasOpenSession = false
            )
        )
    }

    @Test
    fun openSessionIsNeverReplacedDuringCatalogueRecovery() {
        assertFalse(
            shouldRepairMissingDhikrSelection(
                storedDhikr = "",
                hasValidSelection = false,
                hasOpenSession = true
            )
        )
    }

    @Test
    fun onlyUserCreatedMyDhikrsCanBeSelectedByTheTasbeehCounter() {
        val custom = DhikrItem(
            id = "custom-my-dhikr",
            value = "My remembrance",
            title = "My remembrance",
            category = "My Dhikhrs",
            arabic = "سُبْحَانَ اللَّهِ",
            transliteration = "SubhanAllah",
            meaning = "Glory be to Allah",
            customId = "custom-1",
            categories = listOf("My Dhikhrs"),
            contentType = "custom"
        )
        val quran = DhikrItem(
            id = "quran-surah-002",
            value = "Surah Al-Baqarah",
            title = "Surah Al-Baqarah",
            category = "Quran",
            arabic = "",
            transliteration = "",
            meaning = "",
            quranAyahKeys = listOf("2:1"),
            categories = listOf("Quran")
        )
        val openDua = DhikrItem(
            id = "opendua-dua",
            value = "Morning dua",
            title = "Morning dua",
            category = "Morning",
            arabic = "اللَّهُمَّ",
            transliteration = "",
            meaning = "",
            recitationAudioUrls = listOf("https://audio.opendua.org/hisn/example.mp3"),
            categories = listOf("Morning")
        )

        assertTrue(isCounterSelectableDhikr(custom))
        assertFalse(isCounterSelectableDhikr(quran))
        assertFalse(isCounterSelectableDhikr(openDua))
    }

    @Test
    fun firstPersonalDhikrIsSelectedOnlyWhenNoPersonalSelectionExists() {
        assertTrue(shouldSelectFirstMyDhikr(hasSelectedCustomDhikr = false, customDhikrCount = 1))
        assertFalse(shouldSelectFirstMyDhikr(hasSelectedCustomDhikr = true, customDhikrCount = 1))
        assertFalse(shouldSelectFirstMyDhikr(hasSelectedCustomDhikr = false, customDhikrCount = 0))
    }

    @Test
    fun legacyBuiltInCounterSelectionIsClearedWithoutReplacingItWithQuran() {
        assertTrue(
            shouldClearCounterDhikrSelection(
                hasSelectedCustomDhikr = false,
                hasCustomDhikrs = false,
                hasLegacySelection = true,
                hasOpenSession = false
            )
        )
        assertFalse(
            shouldClearCounterDhikrSelection(
                hasSelectedCustomDhikr = true,
                hasCustomDhikrs = true,
                hasLegacySelection = true,
                hasOpenSession = true
            )
        )
        assertFalse(
            shouldClearCounterDhikrSelection(
                hasSelectedCustomDhikr = false,
                hasCustomDhikrs = false,
                hasLegacySelection = false,
                hasOpenSession = false
            )
        )
    }
}
