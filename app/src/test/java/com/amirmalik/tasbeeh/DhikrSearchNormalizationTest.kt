package com.amirmalik.tasbeeh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DhikrSearchNormalizationTest {

    @Test
    fun arabicSearchIgnoresVowelMarksAndTatweel() {
        assertEquals(
            "سبحان الله",
            normalizeDhikrSearchText("سُبْحَانَ الـلَّهِ")
        )
    }

    @Test
    fun unvowelledArabicQueryMatchesCatalogText() {
        val catalogText = normalizeDhikrSearchText("سُبْحَانَ اللَّهِ")
        val query = normalizeDhikrSearchText("سبحان الله")
        assertTrue(catalogText.contains(query))
    }

    @Test
    fun englishSearchIgnoresCaseAndExtraWhitespace() {
        assertEquals("subhan allah", normalizeDhikrSearchText("  SUBHAN   ALLAH  "))
    }
}
