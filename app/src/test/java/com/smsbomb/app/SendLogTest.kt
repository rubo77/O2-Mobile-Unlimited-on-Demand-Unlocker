package com.smsbomb.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendLogTest {

    private fun entry(n: Int, success: Boolean = true) =
        LogEntry(
            timestampMillis = n.toLong(),
            phoneNumber = "+49$n",
            messagePreview = "msg$n",
            success = success,
            error = if (success) null else "err$n",
        )

    @Test
    fun `entries are returned in insertion order`() {
        val log = SendLog()
        repeat(3) { log.add(entry(it)) }
        assertEquals(listOf(0L, 1L, 2L), log.entries().map { it.timestampMillis })
    }

    @Test
    fun `size reflects number of entries`() {
        val log = SendLog()
        repeat(7) { log.add(entry(it)) }
        assertEquals(7, log.size)
    }

    @Test
    fun `capacity evicts oldest entries`() {
        val log = SendLog(capacity = 5)
        repeat(8) { log.add(entry(it)) }
        assertEquals(5, log.size)
        assertEquals(listOf(3L, 4L, 5L, 6L, 7L), log.entries().map { it.timestampMillis })
    }

    @Test
    fun `clear empties the log`() {
        val log = SendLog()
        repeat(3) { log.add(entry(it)) }
        log.clear()
        assertTrue(log.entries().isEmpty())
        assertEquals(0, log.size)
    }

    @Test
    fun `failed entries keep their error`() {
        val log = SendLog()
        log.add(entry(0, success = false))
        assertEquals("err0", log.entries().single().error)
    }
}
