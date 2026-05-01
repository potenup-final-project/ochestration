package com.pg.ochestration.application.service

import com.pg.ochestration.domain.exception.PaymentNotFoundException
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import com.pg.ochestration.infrastructure.persistence.jpa.PaymentRepository
import com.pg.ochestration.presentation.web.dto.PaymentCancelResponse
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class UnifiedPaymentService(
    private val approvePaymentHandler: ApprovePaymentHandler,
    private val cancelPaymentHandler: CancelPaymentHandler,
    private val paymentRepository: PaymentRepository
) {
    suspend fun approve(
        principal: MerchantPrincipal,
        orderId: String,
        amount: Long,
        currency: String,
        idempotencyKey: String?,
        requestedAt: Instant?,
        preferredPrimaryProvider: Provider?,
        metadata: Map<String, String>
    ): Payment = approvePaymentHandler.handle(
        principal = principal,
        orderId = orderId,
        amount = amount,
        currency = currency,
        idempotencyKey = idempotencyKey,
        requestedAt = requestedAt,
        preferredPrimaryProvider = preferredPrimaryProvider,
        metadata = metadata
    )

    suspend fun getPayment(merchantId: String, paymentId: String): Payment {
        val payment = paymentRepository.findById(paymentId)
            ?: throw PaymentNotFoundException(paymentId)
        payment.ensureOwnedBy(merchantId)
        return payment
    }

    suspend fun cancel(
        principal: MerchantPrincipal,
        paymentId: String,
        reason: String,
        idempotencyKey: String?,
        requestedAt: Instant?
    ): PaymentCancelResponse = cancelPaymentHandler.handle(
        principal = principal,
        paymentId = paymentId,
        reason = reason,
        idempotencyKey = idempotencyKey,
        requestedAt = requestedAt
    )
}
