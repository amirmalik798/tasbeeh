package com.amirmalik.tasbeeh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranBasmalaTest {

    @Test
    fun stripsUthmaniBasmalaWithAlifWaslaFromTheStartOfAnAyah() {
        val ayah = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ يَٰٓأَيُّهَا الَّذِينَ"

        assertEquals(
            "يَٰٓأَيُّهَا الَّذِينَ",
            QuranBasmala.stripLeadingPrefix(ayah)
        )
    }

    @Test
    fun stripsStandardBasmalaFromTheStartOfAnAyah() {
        val ayah = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ يَا أَيُّهَا الَّذِينَ"

        assertEquals(
            "يَا أَيُّهَا الَّذِينَ",
            QuranBasmala.stripLeadingPrefix(ayah)
        )
    }

    @Test
    fun recognizesUthmaniAlifWaslaStandaloneBasmala() {
        assertTrue(
            QuranBasmala.isStandaloneBasmala(
                "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"
            )
        )
    }

    @Test
    fun doesNotStripBasmalaWhenItDoesNotLeadTheAyah() {
        val ayah = "يَا أَيُّهَا الَّذِينَ بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"

        assertEquals(ayah, QuranBasmala.stripLeadingPrefix(ayah))
    }

    @Test
    fun openingBasmalaIsShownForAllSurahsExceptAtTawbah() {
        assertTrue(QuranBasmala.shouldShowForSurah(1))
        assertTrue(QuranBasmala.shouldShowForSurah(5))
        assertFalse(QuranBasmala.shouldShowForSurah(9))
        assertFalse(QuranBasmala.shouldShowForSurah(null))
    }
}
