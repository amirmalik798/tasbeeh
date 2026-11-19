package com.amirmalik.tasbeeh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedRecitationUrlTest {

    @Test
    fun acceptsOnlyTheDocumentedHttpsAudioProviders() {
        assertTrue(isTrustedRecitationUrl("https://verses.quran.foundation/AbdulBaset/Mujawwad/mp3/001001.mp3"))
        assertTrue(isTrustedRecitationUrl("https://audio.opendua.org/hisn/recitation.mp3"))
        assertTrue(isTrustedRecitationUrl("https://audio.opendua.org:443/hisn/recitation.mp3"))
    }

    @Test
    fun rejectsLookalikeHostsAndUserInfoSpoofing() {
        assertFalse(isTrustedRecitationUrl("https://audio.opendua.org.attacker.example/file.mp3"))
        assertFalse(isTrustedRecitationUrl("https://verses.quran.foundation.attacker.example/file.mp3"))
        assertFalse(isTrustedRecitationUrl("https://audio.opendua.org@attacker.example/file.mp3"))
    }

    @Test
    fun rejectsInsecureUnexpectedOrMalformedUrls() {
        assertFalse(isTrustedRecitationUrl("http://audio.opendua.org/hisn/file.mp3"))
        assertFalse(isTrustedRecitationUrl("https://audio.opendua.org:444/hisn/file.mp3"))
        assertFalse(isTrustedRecitationUrl("https://example.com/file.mp3"))
        assertFalse(isTrustedRecitationUrl("javascript:alert(1)"))
        assertFalse(isTrustedRecitationUrl("not a URL"))
        assertFalse(isTrustedRecitationUrl(""))
    }
}
