package com.pg.ochestration.application.service

import com.pg.ochestration.application.port.out.GatewayCancelCommand
import com.pg.ochestration.application.port.out.PaymentProviderGateway
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.presentation.web.dto.PaymentCancelResponse
import com.pg.ochestration.presentation.web.dto.PaymentFailureView
import com.pg.ochestration.application.orchestration.ApprovePaymentCommand
import com.pg.ochestration.application.orchestration.PgOrchestrator
import com.pg.ochestration.infrastructure.persistence.memory.PaymentRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class UnifiedPaymentService(
    private val pgOrchestrator: PgOrchestrator,
    private val paymentRepository: PaymentRepository,
    private val gateways: List<PaymentProviderGateway>
) {
    suspend fun approve(
        merchantId: String,
        orderId: String,
        amount: Long,
        currency: String,
        idempotencyKey: String?,
        requestedAt: Instant?,
        preferredPrimaryProvider: Provider?,
        metadata: Map<String, String>
    ): Payment {
        return pgOrchestrator.approve(
            ApprovePaymentCommand(
                merchantId = merchantId,
                orderId = orderId,
                amount = amount,
                currency = currency,
                idempotencyKey = idempotencyKey ?: UUID.randomUUID().toString(),
                requestedAt = requestedAt ?: Instant.now(),
                preferredPrimaryProvider = preferredPrimaryProvider,
                metadata = metadata
            )
        )
    }

    suspend fun getPayment(paymentId: String): Payment {
        return paymentRepository.findById(paymentId)
            ?: throw IllegalArgumentException("Payment not found: $paymentId")
    }

    suspend fun cancel(
        paymentId: String,
        reason: String,
        idempotencyKey: String?,
        requestedAt: Instant?
    ): PaymentCancelResponse {
        val payment = paymentRepository.findById(paymentId)
            ?: throw IllegalArgumentException("Payment not found: $paymentId")

        val approvedProvider = payment.approvedProvider
            ?: throw IllegalStateException("Payment cannot be canceled because approvedProvider is null")
        val providerTxId = payment.providerTxId
            ?: throw IllegalStateException("Payment cannot be canceled because providerTxId is null")

        val gateway = gateways.firstOrNull { it.supports(approvedProvider) }
            ?: throw IllegalStateException("No gateway for provider=$approvedProvider")

        val cancelResult = gateway.cancel(
            GatewayCancelCommand(
                merchantId = payment.merchantId,
                paymentId = payment.paymentId,
                providerTxId = providerTxId,
                reason = reason,
                idempotencyKey = idempotencyKey ?: UUID.randomUUID().toString(),
                requestedAt = requestedAt ?: Instant.now()
            )
        )

        val canceled = payment.copy(
            status = cancelResult.status,
            canceledAt = cancelResult.canceledAt,
            cancelReason = reason,
            failureCode = cancelResult.failure?.code,
            failureCategory = cancelResult.failure?.category,
            failureMessage = cancelResult.failure?.message,
            metadata = payment.metadata + cancelResult.metadata
        )
        paymentRepository.save(canceled)

        return PaymentCancelResponse(
            paymentId = paymentId,
            success = cancelResult.success,
            status = cancelResult.status,
            provider = approvedProvider,
            providerTxId = providerTxId,
            canceledAt = cancelResult.canceledAt,
            failure = cancelResult.failure?.let {
                PaymentFailureView(
                    code = it.code,
                    category = it.category,
                    message = it.message
                )
            },
            metadata = cancelResult.metadata
        )
    }
}
