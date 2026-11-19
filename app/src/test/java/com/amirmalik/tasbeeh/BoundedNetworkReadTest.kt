package com.amirmalik.tasbeeh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class BoundedNetworkReadTest {

    @Test
    fun readsUtf8ResponsesWithinTheConfiguredLimit() {
        val content = "Qur'anic text · dhikr · ذكر"
        val bytes = content.toByteArray(Charsets.UTF_8)
        assertEquals(
            content,
            readUtf8WithLimit(ByteArrayInputStream(bytes), bytes.size)
        )
    }

    @Test
    fun rejectsResponsesThatExceedTheConfiguredLimit() {
        val input = ByteArrayInputStream(ByteArray(33) { 65 })
        assertThrows(IOException::class.java) {
            readUtf8WithLimit(input, 32)
        }
    }

    @Test
    fun makesProgressWhenAnInputStreamReturnsZeroForABulkRead() {
        val bytes = "safe read".toByteArray(Charsets.UTF_8)
        val delegate = ByteArrayInputStream(bytes)
        var returnedZero = false
        val unusualStream = object : InputStream() {
            override fun read(): Int = delegate.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (!returnedZero) {
                    returnedZero = true
                    return 0
                }
                return delegate.read(buffer, offset, length)
            }
        }

        assertEquals("safe read", readUtf8WithLimit(unusualStream, bytes.size))
    }

    @Test
    fun rejectsInvalidLimits() {
        assertThrows(IllegalArgumentException::class.java) {
            readUtf8WithLimit(ByteArrayInputStream(byteArrayOf()), 0)
        }
    }
}
