package com.amirmalik.tasbeeh

import java.net.URI
import java.util.Locale

/**
 * Accept audio only from the two providers the app documents. Parse the URL
 * instead of trusting a string prefix, which can be spoofed by a look-alike
 * hostname or user-info segment.
 */
internal fun isTrustedRecitationUrl(value: String): Boolean = runCatching {
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
