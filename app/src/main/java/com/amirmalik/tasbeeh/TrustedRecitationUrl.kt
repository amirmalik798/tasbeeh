package com.amirmalik.tasbeeh

import java.net.URI
import java.util.Locale

/**
 * Accept audio only from the two providers the app documents. Parse the URL
 * instead of trusting a string prefix, which can be spoofed by a look-alike
 * hostname or user-info segment.
 */
private const val MAX_TRUSTED_RECITATION_URL_CHARS = 2_048

internal fun isTrustedRecitationUrl(value: String): Boolean {
    if (value.length > MAX_TRUSTED_RECITATION_URL_CHARS) return false
    return runCatching {
    val uri = URI(value.trim())
    val host = uri.host?.lowercase(Locale.ROOT) ?: return@runCatching false
    uri.isAbsolute &&
        uri.scheme.equals("https", ignoreCase = true) &&
        uri.rawUserInfo == null &&
        (uri.port == -1 || uri.port == 443) &&
        !uri.isOpaque &&
        uri.rawPath.isNotBlank() &&
        host in setOf("verses.quran.foundation", "audio.opendua.org")
}.getOrDefault(false)
}

/** OpenDua recordings must come from its exact audio host, not a look-alike URL. */
internal fun isTrustedOpenDuaAudioUrl(value: String): Boolean =
    isTrustedRecitationUrl(value) && runCatching {
        URI(value.trim()).host.equals("audio.opendua.org", ignoreCase = true)
    }.getOrDefault(false)
