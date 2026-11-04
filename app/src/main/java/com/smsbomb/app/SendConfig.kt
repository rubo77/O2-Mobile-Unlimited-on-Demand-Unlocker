package com.smsbomb.app

/** Immutable configuration for one periodic SMS job. */
data class SendConfig(
    val phoneNumber: String,
    val message: String,
    val intervalMillis: Long,
    val randomFactorPercent: Int = 0,
    val endAtMillis: Long? = null,
    val maxSmsCount: Int? = null,
) {
    /** Returns all validation problems; empty set means the config is usable. */
    fun validationErrors(nowMillis: Long = System.currentTimeMillis()): Set<ValidationError> {
        val errors = mutableSetOf<ValidationError>()
        if (phoneNumber.isBlank()) {
            errors += ValidationError.EMPTY_NUMBER
        } else if (!PHONE_NUMBER_PATTERN.matches(phoneNumber.trim())) {
            errors += ValidationError.INVALID_NUMBER
        }
        if (message.isBlank()) errors += ValidationError.EMPTY_MESSAGE
        if (intervalMillis <= 0) {
            errors += ValidationError.NON_POSITIVE_INTERVAL
        } else if (intervalMillis < MIN_INTERVAL_MILLIS) {
            // Abuse guard: sub-10-minute loops would turn the app into a spam tool.
            errors += ValidationError.INTERVAL_TOO_SHORT
        }
        if (randomFactorPercent !in MIN_RANDOM_FACTOR_PERCENT..MAX_RANDOM_FACTOR_PERCENT) {
            errors += ValidationError.INVALID_RANDOM_FACTOR
        }
        if (endAtMillis != null && endAtMillis <= nowMillis) {
            errors += ValidationError.END_TIME_IN_PAST
        }
        if (maxSmsCount != null && maxSmsCount <= 0) {
            errors += ValidationError.INVALID_MAX_SMS_COUNT
        }
        return errors
    }

    fun isValid(): Boolean = validationErrors().isEmpty()

    enum class ValidationError {
        EMPTY_NUMBER,
        INVALID_NUMBER,
        EMPTY_MESSAGE,
        NON_POSITIVE_INTERVAL,
        INTERVAL_TOO_SHORT,
        INVALID_RANDOM_FACTOR,
        END_TIME_IN_PAST,
        INVALID_MAX_SMS_COUNT,
    }

    companion object {
        const val MIN_INTERVAL_MILLIS = 10 * 60_000L
        const val MIN_RANDOM_FACTOR_PERCENT = 0
        const val MAX_RANDOM_FACTOR_PERCENT = 100

        // Digits with an optional leading '+', separators (space, '-', '/', '(', ')', '.')
        // allowed after the first digit. Length 4..20 characters total.
        private val PHONE_NUMBER_PATTERN = Regex("^\\+?[0-9][0-9\\s()/\\-.]{2,18}[0-9]$")
    }
}
