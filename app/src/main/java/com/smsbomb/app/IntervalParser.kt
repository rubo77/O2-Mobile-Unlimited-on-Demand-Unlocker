package com.smsbomb.app

/** Time unit chosen in the UI, with its length in milliseconds. */
enum class IntervalUnit(val millisPerUnit: Long) {
    MINUTES(60_000L),
    HOURS(3_600_000L),
}

object IntervalParser {

    /** Parses a positive integer amount combined with [unit] into milliseconds, or null if invalid. */
    fun toMillis(amount: String, unit: IntervalUnit): Long? {
        val value = amount.trim().toLongOrNull() ?: return null
        if (value <= 0) return null
        val millis = value * unit.millisPerUnit
        // Guard against overflow for absurd inputs.
        return if (millis / unit.millisPerUnit == value) millis else null
    }

    /** Formats a millisecond interval as a compact human-readable string like "5min" or "1h 30min". */
    fun format(millis: Long): String {
        if (millis <= 0) return "0 s"
        val hours = millis / IntervalUnit.HOURS.millisPerUnit
        val minutes = (millis % IntervalUnit.HOURS.millisPerUnit) / IntervalUnit.MINUTES.millisPerUnit
        val seconds = (millis % IntervalUnit.MINUTES.millisPerUnit) / 1_000L
        return buildString {
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}min ")
            if (seconds > 0 || (hours == 0L && minutes == 0L)) append("${seconds}s")
        }.trim()
    }
}
