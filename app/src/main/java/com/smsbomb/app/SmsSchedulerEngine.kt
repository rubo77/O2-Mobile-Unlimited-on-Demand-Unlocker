package com.smsbomb.app

/**
 * Pure scheduling logic for periodic SMS sending.
 *
 * The service calls [start] once and then invokes [onTick] whenever its timer fires;
 * the engine decides whether a message is due and asks the [SmsSender] to send it.
 * The first message is sent after one full interval, not immediately.
 */
class SmsSchedulerEngine(
    private val config: SendConfig,
    private val sender: SmsSender,
    private val clock: () -> Long,
    private val onSend: (SendResult) -> Unit = {},
) {
    var running = false
        private set
    var sentCount = 0
        private set
    var lastResult: SendResult? = null
        private set

    private var nextSendAtMillis: Long? = null

    fun start() {
        running = true
        nextSendAtMillis = clock() + config.intervalMillis
    }

    fun stop() {
        running = false
        nextSendAtMillis = null
    }

    /** Milliseconds until the next scheduled send, or null when not running. */
    fun millisUntilNextSend(): Long? =
        nextSendAtMillis?.let { (it - clock()).coerceAtLeast(0L) }

    /**
     * Checks whether a message is due and sends it.
     * Returns the [SendResult] when a send happened, null otherwise.
     * The next slot is computed from "now" rather than the missed slot so a delayed
     * tick can never trigger a burst of catch-up messages.
     */
    fun onTick(): SendResult? {
        if (!running) return null
        val next = nextSendAtMillis ?: return null
        val now = clock()
        if (now < next) return null

        val result = sender.send(config.phoneNumber, config.message)
        sentCount++
        lastResult = result
        nextSendAtMillis = now + config.intervalMillis
        onSend(result)
        return result
    }
}
