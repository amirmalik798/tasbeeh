package com.amirmalik.tasbeeh

/**
 * Presentation-only handling for the opening basmala in the full-surah reader.
 *
 * Ayah source data and audio mappings are deliberately left unchanged. Most
 * surahs display the basmala as an unnumbered opening; in Al-Fatihah the source
 * may provide it as ayah 1, so that ayah is represented in the opening block
 * instead of being duplicated in the numbered list. At-Tawbah has no opening
 * basmala.
 */
internal object QuranBasmala {
    const val ARABIC_TEXT = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"

    private const val NORMALIZED_ARABIC = "بسماللهالرحمنالرحيم"

    fun shouldShowForSurah(surahNumber: Int?): Boolean =
        surahNumber != null && surahNumber in 1..114 && surahNumber != 9

    fun stripLeadingPrefix(text: String): String {
        val trimmed = text.trimStart()
        if (!normalizedArabicLetters(trimmed).startsWith(NORMALIZED_ARABIC)) return text

        var consumedLetters = 0
        var sourceOffset = 0
        while (sourceOffset < trimmed.length && consumedLetters < NORMALIZED_ARABIC.length) {
            val character = trimmed[sourceOffset]
            if (isArabicLetter(character)) consumedLetters++
            sourceOffset++
        }

        // Keep combining marks attached to the final basmala letter out of
        // the remaining ayah, then remove only the separator before that text.
        while (sourceOffset < trimmed.length &&
            (!isArabicLetter(trimmed[sourceOffset]) || trimmed[sourceOffset].isWhitespace())
        ) {
            sourceOffset++
        }
        return trimmed.substring(sourceOffset).trimStart()
    }

    fun isStandaloneBasmala(text: String): Boolean =
        normalizedArabicLetters(text) == NORMALIZED_ARABIC

    private fun normalizedArabicLetters(text: String): String = buildString {
        text.forEach { character ->
            when {
                // Uthmani editions commonly use alif wasla (ٱ) where the
                // presentation string uses a plain alif (ا).
                character.code == 0x0671 -> append(0x0627.toChar())
                isArabicLetter(character) -> append(character)
            }
        }
    }

    private fun isArabicLetter(character: Char): Boolean {
        val code = character.code
        // Harakat, tatweel and the Uthmani dagger alif are not base letters.
        if (code in 0x064B..0x065F || code == 0x0670 || code == 0x0640) return false
        return code in 0x0621..0x064A ||
            code in 0x066E..0x06D3 ||
            code in 0x06FA..0x06FC
    }
}
