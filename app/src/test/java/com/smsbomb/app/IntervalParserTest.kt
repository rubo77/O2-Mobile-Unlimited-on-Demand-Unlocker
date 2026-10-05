package com.smsbomb.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntervalParserTest {

    @Test
    fun `parses minutes`() {
        assertEquals(300_000L, IntervalParser.toMillis("5", IntervalUnit.MINUTES))
    }

    @Test
    fun `parses hours`() {
        assertEquals(7_200_000L, IntervalParser.toMillis("2", IntervalUnit.HOURS))
    }

    @Test
    fun `trims whitespace`() {
        assertEquals(60_000L, IntervalParser.toMillis("  1  ", IntervalUnit.MINUTES))
    }

    @Test
    fun `rejects non-numeric input`() {
        assertNull(IntervalParser.toMillis("abc", IntervalUnit.MINUTES))
    }

    @Test
    fun `rejects empty input`() {
        assertNull(IntervalParser.toMillis("", IntervalUnit.MINUTES))
    }

    @Test
    fun `rejects zero`() {
        assertNull(IntervalParser.toMillis("0", IntervalUnit.MINUTES))
    }

    @Test
    fun `rejects negative`() {
        assertNull(IntervalParser.toMillis("-5", IntervalUnit.MINUTES))
    }

    @Test
    fun `rejects decimal`() {
        assertNull(IntervalParser.toMillis("1.5", IntervalUnit.MINUTES))
    }

    @Test
    fun `rejects overflow`() {
        assertNull(IntervalParser.toMillis("${Long.MAX_VALUE}", IntervalUnit.HOURS))
    }

    @Test
    fun `formats seconds`() {
        assertEquals("45s", IntervalParser.format(45_000L))
    }

    @Test
    fun `formats minutes and seconds`() {
        assertEquals("5min 30s", IntervalParser.format(330_000L))
    }

    @Test
    fun `formats hours`() {
        assertEquals("2h 15min", IntervalParser.format(8_100_000L))
    }

    @Test
    fun `formats zero`() {
        assertEquals("0 s", IntervalParser.format(0L))
    }

    @Test
    fun `formats negative as zero`() {
        assertEquals("0 s", IntervalParser.format(-100L))
    }

    @Test
    fun `format of exact minutes has no seconds suffix`() {
        assertEquals("3min", IntervalParser.format(180_000L))
    }
}
