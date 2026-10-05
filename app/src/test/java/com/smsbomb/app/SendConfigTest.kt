package com.smsbomb.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendConfigTest {

    private fun config(
        number: String = "+491234567890",
        message: String = "hello",
        interval: Long = 600_000L,
    ) = SendConfig(number, message, interval)

    @Test
    fun `valid config has no errors`() {
        assertTrue(config().isValid())
        assertTrue(config().validationErrors().isEmpty())
    }

    @Test
    fun `blank number is rejected`() {
        val errors = config(number = "   ").validationErrors()
        assertTrue(SendConfig.ValidationError.EMPTY_NUMBER in errors)
    }

    @Test
    fun `number with letters is rejected`() {
        val errors = config(number = "abc123").validationErrors()
        assertTrue(SendConfig.ValidationError.INVALID_NUMBER in errors)
    }

    @Test
    fun `number that is too short is rejected`() {
        val errors = config(number = "12").validationErrors()
        assertTrue(SendConfig.ValidationError.INVALID_NUMBER in errors)
    }

    @Test
    fun `number with separators is accepted`() {
        assertTrue(config(number = "+49 123/456-789").isValid())
        assertTrue(config(number = "030 123456").isValid())
    }

    @Test
    fun `empty message is rejected`() {
        val errors = config(message = "").validationErrors()
        assertTrue(SendConfig.ValidationError.EMPTY_MESSAGE in errors)
    }

    @Test
    fun `blank message is rejected`() {
        val errors = config(message = "  ").validationErrors()
        assertTrue(SendConfig.ValidationError.EMPTY_MESSAGE in errors)
    }

    @Test
    fun `zero interval is rejected`() {
        val errors = config(interval = 0L).validationErrors()
        assertTrue(SendConfig.ValidationError.NON_POSITIVE_INTERVAL in errors)
    }

    @Test
    fun `negative interval is rejected`() {
        val errors = config(interval = -500L).validationErrors()
        assertTrue(SendConfig.ValidationError.NON_POSITIVE_INTERVAL in errors)
    }

    @Test
    fun `interval below ten minutes is rejected`() {
        val errors = config(interval = 599_999L).validationErrors()
        assertTrue(SendConfig.ValidationError.INTERVAL_TOO_SHORT in errors)
        assertFalse(SendConfig.ValidationError.NON_POSITIVE_INTERVAL in errors)
    }

    @Test
    fun `exactly ten minutes is accepted`() {
        assertTrue(config(interval = 600_000L).isValid())
    }

    @Test
    fun `multiple errors are reported together`() {
        val errors = config(number = "", message = "", interval = -1L).validationErrors()
        assertEquals(
            setOf(
                SendConfig.ValidationError.EMPTY_NUMBER,
                SendConfig.ValidationError.EMPTY_MESSAGE,
                SendConfig.ValidationError.NON_POSITIVE_INTERVAL,
            ),
            errors,
        )
    }

    @Test
    fun `isValid is false when errors exist`() {
        assertFalse(config(number = "").isValid())
    }
}
