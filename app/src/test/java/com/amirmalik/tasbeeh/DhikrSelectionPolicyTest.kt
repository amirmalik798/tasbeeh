package com.amirmalik.tasbeeh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DhikrSelectionPolicyTest {

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
}
