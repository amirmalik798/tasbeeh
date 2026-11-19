package com.amirmalik.tasbeeh.voice

import com.amirmalik.tasbeeh.data.CustomDhikr
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Deterministic text normalization used for both enrollment and finalized ASR results.
 * This is phrase recognition only; it does not identify or verify a speaker.
 */
internal fun normalizeDhikrPhrase(value: String): String {
    if (value.isBlank()) return ""
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
    val output = StringBuilder(normalized.length)
    normalized.forEach { original ->
        val mapped = when (original) {
            'أ', 'إ', 'آ', 'ٱ' -> 'ا'
            'ى' -> 'ي'
            'ؤ' -> 'و'
            'ئ' -> 'ي'
            'ک' -> 'ك'
            'ے' -> 'ي'
            else -> original
        }
        val type = Character.getType(mapped)
        val isArabicMark = mapped in '\u064B'..'\u065F' ||
            mapped == '\u0670' ||
            mapped in '\u06D6'..'\u06ED'
        if (mapped == '\u0640' || isArabicMark ||
            type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
        ) {
            return@forEach
        }
        if (Character.isLetterOrDigit(mapped)) {
            output.append(mapped)
        } else if (output.isNotEmpty() && output.last() != ' ') {
            output.append(' ')
        }
    }
    return output.toString().trim().replace(WHITESPACE, " ")
}

private val WHITESPACE = Regex("\\s+")

/**
 * A conservative comparison. Very short Dhikr phrases require an exact normalized
 * match because a single phonetic error can change most of their meaning.
 */
internal fun isDhikrPhraseMatch(expected: String, recognized: String): Boolean {
    val left = normalizeDhikrPhrase(expected)
    val right = normalizeDhikrPhrase(recognized)
    if (left.isBlank() || right.isBlank()) return false
    if (left == right) return true

    val shortest = minOf(left.length, right.length)
    val longest = maxOf(left.length, right.length)
    if (shortest <= 5 || longest == 0 || shortest.toFloat() / longest < 0.86f) return false

    val distance = levenshteinDistance(left, right)
    val similarity = 1f - distance.toFloat() / longest
    return similarity >= if (shortest <= 10) 0.94f else 0.90f
}

private fun levenshteinDistance(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    var previous = IntArray(right.length + 1) { it }
    var current = IntArray(right.length + 1)
    left.forEachIndexed { leftIndex, leftChar ->
        current[0] = leftIndex + 1
        right.forEachIndexed { rightIndex, rightChar ->
            val substitution = previous[rightIndex] + if (leftChar == rightChar) 0 else 1
            current[rightIndex + 1] = minOf(
                current[rightIndex] + 1,
                previous[rightIndex + 1] + 1,
                substitution
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[right.length]
}

internal fun voiceConfigurationHash(arabic: String, transliteration: String): String {
    val source = buildString {
        append(normalizeDhikrPhrase(arabic))
        append('|')
        append(normalizeDhikrPhrase(transliteration))
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
}

internal fun CustomDhikr.hasUsableVoiceProfile(): Boolean =
    voiceReferenceText.isNotBlank() &&
        voiceProfileSourceHash.isNotBlank() &&
        voiceProfileSourceHash == voiceConfigurationHash(arabic, transliteration)
