package com.amirmalik.tasbeeh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranRecitationTest {

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
    fun everyQuranicCatalogItemHasVerifiedAyahAudioMapping() {
        val quranicItems = DhikrCatalog.items.filter {
            it.category == "Qur'anic" ||
                it.id in setOf("ayat-al-kursi", "surah-ikhlas", "surah-falaq", "surah-nas")
        }
        assertTrue(quranicItems.isNotEmpty())
        quranicItems.forEach { item ->
            assertFalse("Missing ayah keys for ${item.id}", item.quranAyahKeys.isEmpty())
            assertEquals(
                "Audio URL count should match ayah mapping for ${item.id}",
                item.quranAyahKeys.size,
                QuranRecitationAudio.audioUrlsForAyahs(item.quranAyahKeys).size
            )
        }
    }

    @Test
    fun nonQuranicDhikrDoesNotGetRoutedToQariAudio() {
        val ordinaryDhikr = DhikrCatalog.byValue("SubhanAllah")
        assertTrue(ordinaryDhikr?.quranAyahKeys.isNullOrEmpty())
    }
}
