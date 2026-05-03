package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.PaymentAccessDeniedException
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
) {
    fun ensureOwnedBy(requestingMerchantId: String) {
        if (this.merchantId != requestingMerchantId)
            throw PaymentAccessDeniedException(paymentId, requestingMerchantId)
    }

    fun ensureCancelable() = status.ensureCancelable(paymentId)

    fun markCanceled(
        canceledAt: Instant,
        reason: String,
        failure: PaymentFailure? = null
    ): Payment {
        ensureCancelable()
        return copy(
            status = PaymentStatus.CANCELED,
            canceledAt = canceledAt,
            cancelReason = reason,
            failureCode = failure?.code,
            failureCategory = failure?.category,
            failureMessage = failure?.message
        )
    }

    fun markFailed(failure: PaymentFailure?): Payment {
        status.ensureCanFail(paymentId)
        return copy(
            status = PaymentStatus.FAILED,
            failureCode = failure?.code,
            failureCategory = failure?.category,
            failureMessage = failure?.message
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Payment) return false
        return paymentId == other.paymentId
    }

    override fun hashCode(): Int = paymentId.hashCode()
}
