package com.pg.ochestration.application.service

import com.pg.ochestration.application.orchestration.command.ApprovePaymentCommand
import com.pg.ochestration.application.orchestration.PgOrchestrator
import com.pg.ochestration.application.port.out.GatewayCancelCommand
import com.pg.ochestration.application.port.out.PaymentProviderGateway
import com.pg.ochestration.application.service.command.UnifiedPaymentApproveCommand
import com.pg.ochestration.application.service.command.UnifiedPaymentCancelCommand
import com.pg.ochestration.application.service.result.PaymentCancelResult
import com.pg.ochestration.application.service.result.PaymentFailureResult
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.WebhookEventType
import com.pg.ochestration.infrastructure.persistence.jpa.PaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

@Service
class UnifiedPaymentService(
    private val pgOrchestrator: PgOrchestrator,
    private val paymentRepository: PaymentRepository,
    private val gateways: List<PaymentProviderGateway>,
    private val sandboxPaymentSimulator: SandboxPaymentSimulator,
    private val webhookPaymentEventPublisher: WebhookPaymentEventPublisher,
    private val transactionTemplate: TransactionTemplate
) {
    suspend fun approve(command: UnifiedPaymentApproveCommand): Payment {
        val approveCommand = ApprovePaymentCommand(
            merchantId = command.merchantId,
            orderId = command.orderId,
            amount = command.amount,
            currency = command.currency,
            idempotencyKey = command.idempotencyKey ?: UUID.randomUUID().toString(),
            requestedAt = command.requestedAt ?: Instant.now(),
            preferredPrimaryProvider = command.preferredPrimaryProvider,
            metadata = command.metadata
        )

        if (command.environment == ApiKeyEnvironment.SANDBOX) {
            return sandboxPaymentSimulator.simulateApprove(approveCommand) { payment ->
                savePaymentAndPublishApproveEvent(payment)
            }
        }

        return pgOrchestrator.approve(approveCommand) { payment ->
            savePaymentAndPublishApproveEvent(payment)
        }
    }

    suspend fun getPayment(merchantId: String, paymentId: String): Payment {
        val payment = paymentRepository.findById(paymentId)
            ?: throw IllegalArgumentException("결제를 찾을 수 없습니다: $paymentId")
        payment.ensureOwnedBy(merchantId)
        return payment
    }

    suspend fun cancel(command: UnifiedPaymentCancelCommand): PaymentCancelResult {
        val payment = paymentRepository.findById(command.paymentId)
            ?: throw IllegalArgumentException("결제를 찾을 수 없습니다: ${command.paymentId}")
        payment.ensureOwnedBy(command.merchantId)

        if (command.environment == ApiKeyEnvironment.SANDBOX) {
            val canceledPayment = sandboxPaymentSimulator.simulateCancel(payment) { canceled ->
                savePaymentAndPublishWebhookEvent(canceled, WebhookEventType.PAYMENT_CANCELED)
            }
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
                reason = command.reason,
                idempotencyKey = command.idempotencyKey ?: UUID.randomUUID().toString(),
                requestedAt = command.requestedAt ?: Instant.now()
            )
        )

        val canceled = payment.copy(
            status = cancelResult.status,
            canceledAt = cancelResult.canceledAt,
            cancelReason = command.reason,
            failureCode = cancelResult.failure?.code,
            failureCategory = cancelResult.failure?.category,
            failureMessage = cancelResult.failure?.message,
            metadata = payment.metadata + cancelResult.metadata
        )
        if (canceled.status == PaymentStatus.CANCELED) {
            savePaymentAndPublishWebhookEvent(canceled, WebhookEventType.PAYMENT_CANCELED)
        } else {
            paymentRepository.save(canceled)
        }

        return PaymentCancelResult(
            paymentId = command.paymentId,
            success = cancelResult.success,
            status = cancelResult.status,
            provider = approvedProvider,
            providerTxId = providerTxId,
            canceledAt = cancelResult.canceledAt,
            failure = cancelResult.failure?.let {
                PaymentFailureResult(code = it.code, category = it.category, message = it.message)
            },
            metadata = cancelResult.metadata
        )
    }

    private fun buildCancelResponse(payment: Payment): PaymentCancelResult {
        val provider = payment.approvedProvider ?: Provider.TOSS
        val providerTxId = payment.providerTxId ?: ""
        return PaymentCancelResult(
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

    private fun savePaymentAndPublishApproveEvent(payment: Payment): Payment {
        val eventType = when (payment.status) {
            PaymentStatus.APPROVED -> WebhookEventType.PAYMENT_APPROVED
            PaymentStatus.FAILED -> WebhookEventType.PAYMENT_FAILED
            PaymentStatus.READY,
            PaymentStatus.CANCELED -> return paymentRepository.save(payment)
        }
        return savePaymentAndPublishWebhookEvent(payment, eventType)
    }

    private fun savePaymentAndPublishWebhookEvent(payment: Payment, eventType: WebhookEventType): Payment {
        return transactionTemplate.execute {
            val saved = paymentRepository.save(payment)
            webhookPaymentEventPublisher.publish(saved, eventType)
            saved
        } ?: error("결제 저장 및 웹훅 delivery 생성 트랜잭션 결과가 없습니다")
    }
}
