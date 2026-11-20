package com.amirmalik.tasbeeh.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelRedirectHostTest {

    @Test
    fun acceptsHuggingFaceHubAndItsOfficialStorageDomains() {
        val trustedHosts = listOf(
            "huggingface.co",
            "cdn-lfs.huggingface.co",
            "cas-bridge.xethub.hf.co",
            "cas-server.xethub.hf.co",
            "cas-server.xethub-eu.hf.co",
            "transfer.xethub.hf.co",
            "transfer.xethub-eu.hf.co",
            "us.aws.cdn.hf.co",
            "us.gcp.cdn.hf.co",
            "cdn-lfs-us-1.hf.co",
            "cdn-lfs-eu-1.hf.co",
            "CDN-LFS-US-1.HF.CO"
        )

        trustedHosts.forEach { host ->
            assertTrue("Expected official model host to be trusted: $host", isTrustedHuggingFaceModelHost(host))
        }
    }

    @Test
    fun rejectsLookalikeAndUnrelatedHosts() {
        val untrustedHosts = listOf(
            "",
            "hf.co.attacker.example",
            "huggingface.co.attacker.example",
            "evil-huggingface.co",
            "not-hf.co",
            "attacker.example",
            "amazonaws.com",
            "googleapis.com"
        )

        untrustedHosts.forEach { host ->
            assertFalse("Expected host to be rejected: $host", isTrustedHuggingFaceModelHost(host))
        }
    }

    @Test
    fun acceptsCaseAndDnsTrailingDotWithoutWeakeningDomainBoundary() {
        assertTrue(isTrustedHuggingFaceModelHost("US.AWS.CDN.HF.CO."))
        assertFalse(isTrustedHuggingFaceModelHost("us.aws.cdn.hf.co.attacker.example."))
    }
}
