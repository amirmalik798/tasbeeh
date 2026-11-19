package com.amirmalik.tasbeeh

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads a network response with a hard upper bound so an unexpectedly large
 * response cannot exhaust the app's memory while being buffered for JSON parsing.
 */
internal fun readUtf8WithLimit(input: InputStream, maxBytes: Int): String {
    require(maxBytes > 0) { "maxBytes must be greater than zero." }
    val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
    val buffer = ByteArray(8 * 1024)
    var totalBytes = 0

    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        if (count > maxBytes - totalBytes) {
            throw IOException("Network response exceeded the $maxBytes-byte safety limit.")
        }
        output.write(buffer, 0, count)
        totalBytes += count
    }

    return String(output.toByteArray(), Charsets.UTF_8)
}

internal const val MAX_OPENDUA_RESPONSE_BYTES = 8 * 1024 * 1024
internal const val MAX_QURAN_SURAH_RESPONSE_BYTES = 2 * 1024 * 1024
internal const val MAX_OPENDUA_CACHE_CHARS = 12 * 1024 * 1024
internal const val MAX_QURAN_CACHE_CHARS = 4 * 1024 * 1024
