package com.pg.ochestration.application.service

import com.pg.ochestration.application.orchestration.ApprovePaymentCommand
import com.pg.ochestration.application.orchestration.PgOrchestrator
import com.pg.ochestration.application.port.out.GatewayCancelCommand
import com.pg.ochestration.application.port.out.PaymentProviderGateway
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import com.pg.ochestration.infrastructure.persistence.jpa.PaymentRepository
import com.pg.ochestration.presentation.web.dto.PaymentCancelResponse
import com.pg.ochestration.presentation.web.dto.PaymentFailureView
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class UnifiedPaymentService(
    private val pgOrchestrator: PgOrchestrator,
    private val paymentRepository: PaymentRepository,
    private val gateways: List<PaymentProviderGateway>,
    private val sandboxPaymentSimulator: SandboxPaymentSimulator
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
    ): Payment {
        val command = ApprovePaymentCommand(
            merchantId = principal.merchantId,
            orderId = orderId,
            amount = amount,
            currency = currency,
            idempotencyKey = idempotencyKey ?: UUID.randomUUID().toString(),
            requestedAt = requestedAt ?: Instant.now(),
            preferredPrimaryProvider = preferredPrimaryProvider,
            metadata = metadata
        )

        if (principal.environment == ApiKeyEnvironment.SANDBOX) {
            return sandboxPaymentSimulator.simulateApprove(command)
        }

        return pgOrchestrator.approve(command)
    }

    suspend fun getPayment(merchantId: String, paymentId: String): Payment {
        val payment = paymentRepository.findById(paymentId)
            ?: throw IllegalArgumentException("결제를 찾을 수 없습니다: $paymentId")
        payment.ensureOwnedBy(merchantId)
        return payment
    }

    suspend fun cancel(
        principal: MerchantPrincipal,
        paymentId: String,
        reason: String,
        idempotencyKey: String?,
        requestedAt: Instant?
    ): PaymentCancelResponse {
        val payment = paymentRepository.findById(paymentId)
            ?: throw IllegalArgumentException("결제를 찾을 수 없습니다: $paymentId")
        payment.ensureOwnedBy(principal.merchantId)

        if (principal.environment == ApiKeyEnvironment.SANDBOX) {
            val canceledPayment = sandboxPaymentSimulator.simulateCancel(payment)
            return buildCancelResponse(canceledPayment)
        }

        val approvedProvider = payment.approvedProvider
            ?: throw IllegalStateException("취소할 수 없습니다: approvedProvider가 없습니다")
        val providerTxId = payment.providerTxId
            ?: throw IllegalStateException("취소할 수 없습니다: providerTxId가 없습니다")

        val gateway = gateways.firstOrNull { it.supports(approvedProvider) }
            ?: throw IllegalStateException("지원하지 않는 PG 프로바이더입니다: $approvedProvider")

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
                PaymentFailureView(code = it.code, category = it.category, message = it.message)
            },
            metadata = cancelResult.metadata
        )
    }

    private fun buildCancelResponse(payment: Payment): PaymentCancelResponse {
        val provider = payment.approvedProvider ?: Provider.TOSS
        val providerTxId = payment.providerTxId ?: ""
        return PaymentCancelResponse(
            paymentId = payment.paymentId,
            success = true,
            status = payment.status,
            provider = provider,
            providerTxId = providerTxId,
            canceledAt = payment.canceledAt,
            failure = null,
            metadata = payment.metadata
        )
    }
}
