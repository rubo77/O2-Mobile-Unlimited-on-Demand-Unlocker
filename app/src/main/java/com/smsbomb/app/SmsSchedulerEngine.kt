package com.smsbomb.app

import kotlin.random.Random

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
    private val randomUnit: () -> Double = { Random.nextDouble() },
    private val onSend: (SendResult) -> Unit = {},
) {
    var running = false
        private set
    var sentCount = 0
        private set
    var lastResult: SendResult? = null
        private set
    var stopReason: StopReason? = null
        private set

    private var nextSendAtMillis: Long? = null

    fun start() {
        stopReason = null
        if (config.endAtMillis != null && config.endAtMillis <= clock()) {
            running = false
            stopReason = StopReason.END_TIME
            nextSendAtMillis = null
            return
        }
        running = true
        nextSendAtMillis = safeAdd(clock(), nextDelayMillis())
    }

    fun stop() {
        running = false
        nextSendAtMillis = null
        stopReason = StopReason.USER_STOP
    }

    /** Milliseconds until the next send or end-time check, or null when not running. */
    fun millisUntilNextEvent(): Long? {
        if (!running) return null
        val now = clock()
        val nextEventAt = listOfNotNull(nextSendAtMillis, config.endAtMillis).minOrNull()
            ?: return null
        return (nextEventAt - now).coerceAtLeast(0L)
    }

    /** Milliseconds until the next SMS, or null when not running. */
    fun millisUntilNextSend(): Long? =
        if (running) nextSendAtMillis?.let { (it - clock()).coerceAtLeast(0L) } else null

    /**
     * Checks whether a message is due and sends it.
     * Returns the [SendResult] when a send happened, null otherwise.
     * The next slot is computed from "now" rather than the missed slot so a delayed
     * tick can never trigger a burst of catch-up messages.
     */
    fun onTick(): SendResult? {
        if (!running) return null
        val now = clock()
        if (config.endAtMillis != null && now >= config.endAtMillis) {
            finish(StopReason.END_TIME)
            return null
        }
        val next = nextSendAtMillis ?: return null
        if (now < next) return null

        val result = sender.send(config.phoneNumber, config.message)
        if (result.success) sentCount++
        lastResult = result
        onSend(result)

        if (result.success && config.maxSmsCount != null && sentCount >= config.maxSmsCount) {
            finish(StopReason.MAX_SMS_COUNT)
        } else {
            nextSendAtMillis = safeAdd(now, nextDelayMillis())
        }
        return result
    }

    private fun nextDelayMillis(): Long {
        if (config.randomFactorPercent == 0) return config.intervalMillis
        val random = randomUnit()
        require(random >= 0.0 && random < 1.0) { "randomUnit must return a value in [0, 1)" }
        val extraMillis = (config.intervalMillis / 100) * config.randomFactorPercent +
            (config.intervalMillis % 100) * config.randomFactorPercent / 100
        return safeAdd(config.intervalMillis, (extraMillis.toDouble() * random).toLong())
    }

    private fun safeAdd(left: Long, right: Long): Long =
        if (right > 0 && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private fun finish(reason: StopReason) {
        running = false
        nextSendAtMillis = null
        stopReason = reason
    }

    enum class StopReason {
        END_TIME,
        MAX_SMS_COUNT,
        USER_STOP,
    }
}
