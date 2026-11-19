package com.amirmalik.tasbeeh

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppNetworkStateTest {

    @Test
    fun networkErrorsAreExplainedInPlainLanguage() {
        val offline = networkFailureMessage("OpenDua", UnknownHostException("host"))
        assertTrue(offline.contains("No internet connection"))
        assertTrue(offline.contains("saved content remains"))

        val timeout = networkFailureMessage("Qur'anic text", SocketTimeoutException("timeout"))
        assertTrue(timeout.contains("Check your connection"))
        assertTrue(timeout.contains("saved content remains"))

        val interrupted = networkFailureMessage("OpenDua", IOException("read failed"))
        assertTrue(interrupted.contains("could not be reached"))
    }

    @Test
    fun storageFailuresOfferSafeRetryGuidance() {
        val diskFull = storeOperationFailureMessage("Saving your count…", IOException("disk full"))
        assertTrue(diskFull.contains("available device storage"))
        assertTrue(diskFull.contains("existing saved data"))

        val unexpected = storeOperationFailureMessage("Deleting your Dhikr…", IllegalStateException())
        assertTrue(unexpected.contains("could not be completed"))
        assertFalse(unexpected.contains("reset"))
    }
}
