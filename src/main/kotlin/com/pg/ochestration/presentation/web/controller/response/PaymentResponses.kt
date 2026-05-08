package com.pg.ochestration.presentation.web.controller.response

import com.pg.ochestration.application.service.result.PaymentCancelResult
import com.pg.ochestration.application.service.result.PaymentFailureResult
import com.pg.ochestration.domain.model.FailureCategory
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentAttempt
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.SelectionSummary
import java.time.Instant

data class PaymentFailureView(
    val code: String,
    val category: FailureCategory,
    val message: String
) {
    companion object {
        fun from(result: PaymentFailureResult): PaymentFailureView =
            PaymentFailureView(code = result.code, category = result.category, message = result.message)
    }
}

data class PaymentCancelResponse(
    val paymentId: String,
    val success: Boolean,
    val status: PaymentStatus,
    val provider: Provider,
    val providerTxId: String,
    val canceledAt: Instant?,
    val failure: PaymentFailureView? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        fun from(result: PaymentCancelResult): PaymentCancelResponse =
            PaymentCancelResponse(
                paymentId = result.paymentId,
                success = result.success,
                status = result.status,
                provider = result.provider,
                providerTxId = result.providerTxId,
                canceledAt = result.canceledAt,
                failure = result.failure?.let { PaymentFailureView.from(it) },
                metadata = result.metadata
            )
    }
}

data class PaymentView(
    val paymentId: String,
    val merchantId: String,
    val orderId: String,
    val amount: Long,
    val currency: String,
    val idempotencyKey: String,
    val requestedAt: Instant,
    val success: Boolean,
    val status: PaymentStatus,
    val provider: Provider?,
    val providerTxId: String?,
    val approvedAt: Instant?,
    val canceledAt: Instant?,
    val failure: PaymentFailureView?,
    val metadata: Map<String, String>,
    val attemptCount: Int,
    val selectionSummary: SelectionSummary,
    val attempts: List<PaymentAttempt>
) {
    companion object {
        fun from(payment: Payment): PaymentView =
            PaymentView(
                paymentId = payment.paymentId,
                merchantId = payment.merchantId,
                orderId = payment.orderId,
                amount = payment.amount,
                currency = payment.currency,
                idempotencyKey = payment.idempotencyKey,
                requestedAt = payment.requestedAt,
                success = payment.status == PaymentStatus.APPROVED || payment.status == PaymentStatus.CANCELED,
                status = payment.status,
                provider = payment.approvedProvider,
                providerTxId = payment.providerTxId,
                approvedAt = payment.approvedAt,
                canceledAt = payment.canceledAt,
                failure = if (payment.failureCode != null && payment.failureCategory != null) {
                    PaymentFailureView(
                        code = payment.failureCode,
                        category = payment.failureCategory,
                        message = payment.failureMessage ?: "Unknown failure"
                    )
                } else {
                    null
                },
                metadata = payment.metadata,
                attemptCount = payment.attempts.size,
                selectionSummary = payment.selectionSummary,
                attempts = payment.attempts
            )
    }
}
