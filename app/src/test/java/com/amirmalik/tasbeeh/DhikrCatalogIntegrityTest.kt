package com.amirmalik.tasbeeh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DhikrCatalogIntegrityTest {

    @Test
    fun builtInLibraryContainsExactlyOneHundredEntries() {
        assertEquals(100, DhikrCatalog.items.size)
    }

    @Test
    fun everyEntryHasUniqueIdentityAndSelectionValue() {
        val items = DhikrCatalog.items
        assertEquals(items.size, items.map { it.id }.toSet().size)
        assertEquals(items.size, items.map { it.value }.toSet().size)
    }

    @Test
    fun everyEntryHasArabicTransliterationMeaningAndSource() {
        DhikrCatalog.items.forEach { item ->
            assertFalse("Blank id", item.id.isBlank())
            assertFalse("Blank value", item.value.isBlank())
            assertFalse("Blank category for " + item.id, item.category.isBlank())
            assertFalse("Missing Arabic for " + item.id, item.arabic.isBlank())
            assertTrue("Arabic script missing for " + item.id, item.arabic.any { it in '\u0600'..'\u06FF' })
            assertFalse("Missing transliteration for " + item.id, item.transliteration.isBlank())
            assertFalse("Missing meaning for " + item.id, item.meaning.isBlank())
            assertFalse("Missing source for " + item.id, item.reference.isBlank())
            assertNotNull("Entry must resolve by its stored value: " + item.value, DhikrCatalog.byValue(item.value))
        }
    }

    @Test
    fun previouslySavedBuiltInSelectionsRemainResolvable() {
        val previouslySavedValues = listOf(
            "SubhanAllah",
            "Alhamdulillah",
            "Allahu Akbar",
            "La ilaha illallah",
            "SubhanAllahi wa bihamdihi",
            "SubhanAllahil-Azim",
            "The four beloved phrases",
            "La hawla wa la quwwata illa billah",
            "Astaghfirullah",
            "Astaghfirullaha wa atubu ilayh",
            "Sayyid al-Istighfar",
            "Allahumma salli 'ala Muhammad",
            "La ilaha illallah wahdahu...",
            "Hasbunallahu wa ni'mal-wakil",
            "Inna lillahi wa inna ilayhi raji'un",
            "Dua of Prophet Yunus",
            "Rabbana atina fid-dunya",
            "Rabbighfir li",
            "A'udhu bi kalimatillah",
            "Bismillahilladhi la yadurru",
            "Rabbi inni lima anzalta",
            "Dua for beneficial knowledge"
        )
        previouslySavedValues.forEach { value ->
            assertNotNull("Previously saved Dhikr no longer resolves: " + value, DhikrCatalog.byValue(value))
        }
    }
}
