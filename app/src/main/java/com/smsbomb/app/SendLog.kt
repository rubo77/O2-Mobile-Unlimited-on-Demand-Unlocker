package com.smsbomb.app

/** One line in the in-app send log. */
data class LogEntry(
    val timestampMillis: Long,
    val phoneNumber: String,
    val messagePreview: String,
    val success: Boolean,
    val error: String? = null,
)

/** Bounded, thread-safe in-memory log of send attempts (newest entries kept). */
class SendLog(private val capacity: Int = DEFAULT_CAPACITY) {

    private val entries = ArrayDeque<LogEntry>()

    @Synchronized
    fun add(entry: LogEntry) {
        entries.addLast(entry)
        while (entries.size > capacity) entries.removeFirst()
    }

    @Synchronized
    fun entries(): List<LogEntry> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()

    val size: Int
        @Synchronized get() = entries.size

    companion object {
        const val DEFAULT_CAPACITY = 200
    }
}

/** Process-wide log shared between the scheduler service and the UI. */
object SharedSendLog {
    val instance = SendLog()
}
