package com.smsbomb.app

/** Process-wide status shared between [SmsSchedulerService] and [MainActivity]. */
object SchedulerState {
    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var sentCount: Int = 0
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var nextSendAtMillis: Long? = null
        private set

    @Synchronized
    fun markRunning(nextSendAt: Long?) {
        running = true
        sentCount = 0
        lastError = null
        nextSendAtMillis = nextSendAt
    }

    @Synchronized
    fun recordSend(result: SendResult, nextSendAt: Long?) {
        sentCount++
        lastError = result.error
        nextSendAtMillis = nextSendAt
    }

    @Synchronized
    fun markStopped() {
        running = false
        nextSendAtMillis = null
    }
}
