package com.pg.ochestration.domain.model

import java.time.Instant

data class PaymentAttempt(
    val attemptNo: Int,
    val provider: Provider,
    val result: AttemptResult,
    val failureCategory: FailureCategory? = null,
    val failureCode: String? = null,
    val failureMessage: String? = null,
    val providerTxId: String? = null,
    val attemptedAt: Instant = Instant.now()
)
