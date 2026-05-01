package com.pg.ochestration.application.service

import com.pg.ochestration.application.port.out.GatewayCancelCommand
import com.pg.ochestration.application.port.out.PaymentProviderGateway
import com.pg.ochestration.domain.exception.PaymentNotFoundException
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentFailure
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import com.pg.ochestration.infrastructure.persistence.jpa.PaymentRepository
import com.pg.ochestration.presentation.web.dto.PaymentCancelResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class CancelPaymentHandler(
    private val paymentRepository: PaymentRepository,
    private val gateways: List<PaymentProviderGateway>,
    private val sandboxPaymentSimulator: SandboxPaymentSimulator
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun handle(
        principal: MerchantPrincipal,
        paymentId: String,
        reason: String,
        idempotencyKey: String?,
        requestedAt: Instant?
    ): PaymentCancelResponse {
        val payment = paymentRepository.findById(paymentId)
            ?: throw PaymentNotFoundException(paymentId)
        payment.ensureOwnedBy(principal.merchantId)
        payment.ensureCancelable()

        if (principal.environment == ApiKeyEnvironment.SANDBOX) {
            return handleSandboxCancel(payment)
        }

        return handleLiveCancel(payment, reason, idempotencyKey, requestedAt)
    }

    private fun handleSandboxCancel(payment: Payment): PaymentCancelResponse {
        val canceledPayment = sandboxPaymentSimulator.simulateCancel(payment)
        val provider = canceledPayment.approvedProvider
            ?: error("Sandbox 취소 응답 생성 실패: approvedProvider가 없습니다 — paymentId=${payment.paymentId}")
        return PaymentCancelResponse(
            paymentId = canceledPayment.paymentId,
            success = true,
            status = canceledPayment.status,
            provider = provider,
            providerTxId = canceledPayment.providerTxId ?: "",
            canceledAt = canceledPayment.canceledAt,
            failure = null,
            metadata = canceledPayment.metadata
        )
    }

    private suspend fun handleLiveCancel(
        payment: Payment,
        reason: String,
        idempotencyKey: String?,
        requestedAt: Instant?
    ): PaymentCancelResponse {
        val approvedProvider = requireNotNull(payment.approvedProvider) {
            "취소 처리 실패: approvedProvider가 없습니다 — paymentId=${payment.paymentId}"
        }
        val providerTxId = requireNotNull(payment.providerTxId) {
            "취소 처리 실패: providerTxId가 없습니다 — paymentId=${payment.paymentId}"
        }

        val gateway = gateways.firstOrNull { it.supports(approvedProvider) }
            ?: error("지원하지 않는 PG 프로바이더입니다: $approvedProvider")

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

        log.info(
            "결제 취소 완료 — paymentId={}, provider={}, success={}",
            payment.paymentId, approvedProvider, cancelResult.success
        )

        val canceledPayment = payment.markCanceled(
            canceledAt = cancelResult.canceledAt ?: Instant.now(),
            reason = reason,
            failure = cancelResult.failure?.let {
                PaymentFailure(code = it.code, category = it.category, message = it.message)
            }
        )
        val canceledWithMeta = withMetadata(canceledPayment, payment.metadata + cancelResult.metadata)
        paymentRepository.save(canceledWithMeta)

        return PaymentCancelResponse.from(payment, cancelResult)
    }

    private fun withMetadata(payment: Payment, metadata: Map<String, String>): Payment =
        Payment(
            paymentId = payment.paymentId,
            merchantId = payment.merchantId,
            orderId = payment.orderId,
            amount = payment.amount,
            currency = payment.currency,
            idempotencyKey = payment.idempotencyKey,
            requestedAt = payment.requestedAt,
            status = payment.status,
            approvedProvider = payment.approvedProvider,
            providerTxId = payment.providerTxId,
            approvedAt = payment.approvedAt,
            canceledAt = payment.canceledAt,
            failureCode = payment.failureCode,
            failureCategory = payment.failureCategory,
            failureMessage = payment.failureMessage,
            attempts = payment.attempts,
            selectionSummary = payment.selectionSummary,
            metadata = metadata,
            cancelReason = payment.cancelReason
        )
}
