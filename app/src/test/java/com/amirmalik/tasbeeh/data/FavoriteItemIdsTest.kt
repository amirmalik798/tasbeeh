package com.amirmalik.tasbeeh.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteItemIdsTest {
    @Test fun favoriteIdsRoundTripWithoutLosingStableIdentities() {
        val ids = listOf("quran-surah-al-fatiha", "opendua-1024", "custom-my-dhikr")
        assertEquals(ids.toSet(), decodeFavoriteItemIds(encodeFavoriteItemIds(ids)))
    }

    @Test fun favoriteToggleAddsAndRemovesTheSameStableId() {
        val initial = setOf("quran-surah-al-fatiha")
        val withDua = toggleFavoriteItemId(initial, "opendua-1024")
        assertTrue("opendua-1024" in withDua)
        val removed = toggleFavoriteItemId(withDua, "opendua-1024")
        assertFalse("opendua-1024" in removed)
        assertEquals(initial, removed)
    }

    @Test fun malformedFavoriteDataDoesNotCrashTheLibraryReadPath() {
        assertTrue(decodeFavoriteItemIds("{invalid").isEmpty())
    }

    @Test fun invalidFavoriteIdsCannotEnterThePersistedSet() {
        val initial = setOf("valid-id")
        assertEquals(initial, toggleFavoriteItemId(initial, "  "))
        assertEquals(initial, toggleFavoriteItemId(initial, "x".repeat(181)))
    }
}
