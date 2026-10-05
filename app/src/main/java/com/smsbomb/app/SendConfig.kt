package com.smsbomb.app

/** Immutable configuration for one periodic SMS job. */
data class SendConfig(
    val phoneNumber: String,
    val message: String,
    val intervalMillis: Long,
) {
    /** Returns all validation problems; empty set means the config is usable. */
    fun validationErrors(): Set<ValidationError> {
        val errors = mutableSetOf<ValidationError>()
        if (phoneNumber.isBlank()) {
            errors += ValidationError.EMPTY_NUMBER
        } else if (!PHONE_NUMBER_PATTERN.matches(phoneNumber.trim())) {
            errors += ValidationError.INVALID_NUMBER
        }
        if (message.isBlank()) errors += ValidationError.EMPTY_MESSAGE
        if (intervalMillis <= 0) errors += ValidationError.NON_POSITIVE_INTERVAL
        return errors
    }

    fun isValid(): Boolean = validationErrors().isEmpty()

    enum class ValidationError {
        EMPTY_NUMBER,
        INVALID_NUMBER,
        EMPTY_MESSAGE,
        NON_POSITIVE_INTERVAL,
    }

    companion object {
        // Digits with an optional leading '+', separators (space, '-', '/', '(', ')', '.')
        // allowed after the first digit. Length 4..20 characters total.
        private val PHONE_NUMBER_PATTERN = Regex("^\\+?[0-9][0-9\\s()/\\-.]{2,18}[0-9]$")
    }
}
