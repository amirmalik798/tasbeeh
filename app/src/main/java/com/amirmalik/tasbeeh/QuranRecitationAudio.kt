package com.amirmalik.tasbeeh

/**
 * Streams authentic Qur'anic recitation instead of synthesizing Qur'anic
 * verses with a generic device TTS engine. Audio is streamed directly from
 * Quran Foundation's documented audio host and is never bundled or rehosted.
 */
internal object QuranRecitationAudio {
    const val RECITER_NAME = "AbdulBaset AbdulSamad"
    const val RECITATION_STYLE = "Mujawwad"
    const val ATTRIBUTION = "Qur'anic recitation by AbdulBaset AbdulSamad · Quran.Foundation"

    private const val AUDIO_BASE =
        "https://verses.quran.foundation/AbdulBaset/Mujawwad/mp3"

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
    fun shouldUseQuranFoundation(ayahKeys: List<String>): Boolean =
        ayahKeys.isNotEmpty() && audioUrlsForAyahs(ayahKeys).isNotEmpty()

    fun audioUrlForAyah(ayahKey: String): String? {
        val parts = ayahKey.split(':')
        if (parts.size != 2) return null
        val surah = parts[0].toIntOrNull() ?: return null
        val ayah = parts[1].toIntOrNull() ?: return null
        if (surah !in 1..114 || ayah !in 1..286) return null

        val fileName = surah.toString().padStart(3, '0') +
            ayah.toString().padStart(3, '0') + ".mp3"
        return "$AUDIO_BASE/$fileName"
    }
}
