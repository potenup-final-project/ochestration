package com.pg.ochestration.domain.model

enum class FailureCategory {
    RETRYABLE_TECHNICAL,
    NON_RETRYABLE_BUSINESS,
    UNKNOWN
}
