package com.amirmalik.tasbeeh.voice

import com.amirmalik.tasbeeh.data.CustomDhikr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class VoicePhraseRecognitionTest {

    @Test
    fun arabicDiacriticsTatweelAndAlefVariantsNormalizeConsistently() {
        assertEquals(
            normalizeDhikrPhrase("الْحَمْدُ لِلَّهِ"),
            normalizeDhikrPhrase("ٱلْحَمْدُ لِلّٰهِ")
        )
        assertEquals(
            normalizeDhikrPhrase("الله أكبر"),
            normalizeDhikrPhrase("اللّٰهُ أَكْبَرُ")
        )
        assertEquals(
            normalizeDhikrPhrase("سبحان الله"),
            normalizeDhikrPhrase("سُبْحَانَ اللَّهِ")
        )
    }

    @Test
    fun exactAndNormalizedArabicPhrasesAreAccepted() {
        assertTrue(isDhikrPhraseMatch("سُبْحَانَ اللَّهِ", "سبحان الله"))
        assertTrue(isDhikrPhraseMatch("أَسْتَغْفِرُ اللَّهَ", "استغفر الله"))
        assertTrue(isDhikrPhraseMatch("ٱلْحَمْدُ لِلَّهِ", "الحمد لله"))
    }

    @Test
    fun unrelatedAndPartialPhrasesAreRejected() {
        assertFalse(isDhikrPhraseMatch("سُبْحَانَ اللَّهِ", "الْحَمْدُ لِلَّهِ"))
        assertFalse(isDhikrPhraseMatch("سُبْحَانَ اللَّهِ", "سُبْحَانَ"))
        assertFalse(isDhikrPhraseMatch("الله أكبر", ""))
        assertFalse(isDhikrPhraseMatch("", "الله أكبر"))
    }

    @Test
    fun veryShortPhrasesRequireExactNormalizedMatch() {
        assertTrue(isDhikrPhraseMatch("الله", "اللّٰه"))
        assertFalse(isDhikrPhraseMatch("الله", "اله"))
    }

    @Test
    fun voiceProfileHashIsStableForEquivalentArabicTextAndChangesWithText() {
        val baseline = voiceConfigurationHash("الْحَمْدُ لِلَّهِ", "Alhamdulillah")
        val equivalent = voiceConfigurationHash("ٱلْحَمْدُ لِلّٰهِ", "Alhamdulillah")
        val changed = voiceConfigurationHash("سُبْحَانَ اللَّهِ", "SubhanAllah")

        assertEquals(baseline, equivalent)
        assertFalse(baseline == changed)
    }

    @Test
    fun legacyCustomDhikrDefaultsToVoiceDisabledAndProfileMustMatchTextHash() {
        val legacy = CustomDhikr(id = "legacy", title = "My Dhikr")
        assertFalse(legacy.hasUsableVoiceProfile())

        val configured = legacy.copy(
            voiceProfileId = UUID.randomUUID().toString(),
            voiceProfileSourceHash = voiceConfigurationHash(legacy.arabic, legacy.transliteration),
            voiceProfileCreatedAt = 100L
        )
        assertTrue(configured.hasUsableVoiceProfile())
        assertFalse(configured.copy(arabic = "الحمد لله").hasUsableVoiceProfile())
        assertFalse(configured.copy(voiceProfileId = "not-a-uuid").hasUsableVoiceProfile())
        assertFalse(configured.copy(voiceProfileCreatedAt = 0L).hasUsableVoiceProfile())
    }
}
