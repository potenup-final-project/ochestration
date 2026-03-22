package com.pg.ochestration.domain.service

import com.pg.ochestration.domain.model.FailureCategory
import org.springframework.stereotype.Component

@Component
class FailureClassifier {
    fun classify(failureCode: String?): FailureCategory {
        return when (failureCode) {
            "CARD_LIMIT_EXCEEDED",
            "ALREADY_PROCESSED_PAYMENT",
            "INVALID_REQUEST",
            "REJECT_CARD_COMPANY" -> FailureCategory.NON_RETRYABLE_BUSINESS

            "PG_TIMEOUT",
            "NETWORK_ERROR",
            "INTERNAL_SERVER_ERROR",
            "TOO_MANY_REQUESTS" -> FailureCategory.RETRYABLE_TECHNICAL

            else -> FailureCategory.UNKNOWN
        }
    }
}
