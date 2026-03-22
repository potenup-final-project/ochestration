package com.pg.ochestration.domain.model

import java.time.Instant

data class Payment(
    val paymentId: String,
    val merchantId: String,
    val orderId: String,
    val amount: Long,
    val currency: String,
    val idempotencyKey: String,
    val requestedAt: Instant,
    val status: PaymentStatus,
    val approvedProvider: Provider?,
    val providerTxId: String? = null,
    val approvedAt: Instant? = null,
    val canceledAt: Instant? = null,
    val failureCode: String? = null,
    val failureCategory: FailureCategory? = null,
    val failureMessage: String? = null,
    val attempts: List<PaymentAttempt>,
    val selectionSummary: SelectionSummary,
    val metadata: Map<String, String> = emptyMap(),
    val cancelReason: String? = null
)
