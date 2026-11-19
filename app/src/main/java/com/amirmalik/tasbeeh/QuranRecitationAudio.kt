package com.amirmalik.tasbeeh

/**
 * Streams complete Qur'anic ayah recitations from Quran Foundation.
 * URLs are constructed only for valid surah/ayah keys; text-to-speech is
 * deliberately not used for Qur'anic or non-Qur'anic entries.
 */
internal object QuranRecitationAudio {
    const val RECITER_NAME = "AbdulBaset AbdulSamad"
    const val RECITATION_STYLE = "Mujawwad"
    const val ATTRIBUTION = "Qur'anic recitation by AbdulBaset AbdulSamad · Quran.Foundation"

    private const val AUDIO_BASE =
        "https://verses.quran.foundation/AbdulBaset/Mujawwad/mp3"

    // Canonical verse counts for surahs 1–114. This prevents syntactically
    // plausible but non-existent verse numbers from generating audio URLs.
    private val ayahCounts = intArrayOf(
        0,
        // 1–10
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109,
        // 11–20
        123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        // 21–30
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
        // 31–40
        34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        // 41–50
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45,
        // 51–60
        60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
        // 61–70
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44,
        // 71–80
        28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        // 81–90
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
        // 91–100
        15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
        // 101–110
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
        // 111–114
        5, 4, 5, 6
    )

    fun audioUrlsForAyahs(ayahKeys: List<String>): List<String> {
        if (ayahKeys.isEmpty()) return emptyList()
        val urls = ayahKeys.map { audioUrlForAyah(it) ?: return emptyList() }
        return urls.distinct()
    }

    /**
     * Quran Foundation playback is only appropriate for entries that carry
     * an explicit, validated ayah mapping. User-authored Arabic text is not
     * inferred to be Qur'anic from its wording or title.
     */
    fun shouldAttemptQuranFoundation(ayahKeys: List<String>): Boolean =
        ayahKeys.isNotEmpty()

    fun shouldUseQuranFoundation(ayahKeys: List<String>): Boolean =
        shouldAttemptQuranFoundation(ayahKeys) && audioUrlsForAyahs(ayahKeys).isNotEmpty()

    fun audioUrlForAyah(ayahKey: String): String? {
        val parts = ayahKey.split(':')
        if (parts.size != 2) return null
        val surah = parts[0].toIntOrNull() ?: return null
        val ayah = parts[1].toIntOrNull() ?: return null
        if (surah !in 1..114 || ayah !in 1..ayahCounts[surah]) return null

        val fileName = surah.toString().padStart(3, '0') +
            ayah.toString().padStart(3, '0') + ".mp3"
        return "$AUDIO_BASE/$fileName"
    }
}
