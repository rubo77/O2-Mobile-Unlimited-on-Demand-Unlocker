package com.smsbomb.app

/** Result of a single SMS send attempt. */
data class SendResult(
    val phoneNumber: String,
    val message: String,
    val timestampMillis: Long,
    val success: Boolean,
    val error: String? = null,
)

/** Abstraction over the platform SMS API so the scheduler stays unit-testable. */
interface SmsSender {
    fun send(phoneNumber: String, message: String): SendResult
}
