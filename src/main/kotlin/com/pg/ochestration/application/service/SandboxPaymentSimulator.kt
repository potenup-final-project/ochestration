package com.pg.ochestration.application.service

import com.pg.ochestration.application.orchestration.ApprovePaymentCommand
import com.pg.ochestration.application.port.out.PaymentSavePort
import com.pg.ochestration.domain.model.FailureCategory
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.SelectionSummary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

private const val SANDBOX_FAILURE_KEYWORD = "fail"
private const val SANDBOX_FORCED_FAILURE_CODE = "SANDBOX_FORCED_FAILURE"
private const val SANDBOX_PROVIDER_TX_ID_PREFIX = "sandbox_tx_"

@Service
class SandboxPaymentSimulator(
    private val paymentSavePort: PaymentSavePort
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun simulateApprove(command: ApprovePaymentCommand): Payment {
        val paymentId = "pay_sandbox_${UUID.randomUUID()}"
        val isForcedFailure = command.orderId.contains(SANDBOX_FAILURE_KEYWORD, ignoreCase = true)

        val payment = if (isForcedFailure) {
            log.info(
                "Sandbox 강제 실패 시뮬레이션 — merchantId={}, orderId={}",
                command.merchantId, command.orderId
            )
            Payment(
                paymentId = paymentId,
                merchantId = command.merchantId,
                orderId = command.orderId,
                amount = command.amount,
                currency = command.currency,
                idempotencyKey = command.idempotencyKey,
                requestedAt = command.requestedAt,
                status = PaymentStatus.FAILED,
                approvedProvider = null,
                providerTxId = null,
                approvedAt = null,
                canceledAt = null,
                failureCode = SANDBOX_FORCED_FAILURE_CODE,
                failureCategory = FailureCategory.NON_RETRYABLE_BUSINESS,
                failureMessage = "Sandbox 강제 실패 (orderId에 'fail' 포함)",
                attempts = emptyList(),
                selectionSummary = sandboxSelectionSummary(),
                metadata = command.metadata
            )
        } else {
            log.info(
                "Sandbox 승인 성공 시뮬레이션 — merchantId={}, orderId={}",
                command.merchantId, command.orderId
            )
            Payment(
                paymentId = paymentId,
                merchantId = command.merchantId,
                orderId = command.orderId,
                amount = command.amount,
                currency = command.currency,
                idempotencyKey = command.idempotencyKey,
                requestedAt = command.requestedAt,
                status = PaymentStatus.APPROVED,
                approvedProvider = Provider.TOSS,
                providerTxId = "$SANDBOX_PROVIDER_TX_ID_PREFIX${UUID.randomUUID()}",
                approvedAt = Instant.now(),
                canceledAt = null,
                failureCode = null,
                failureCategory = null,
                failureMessage = null,
                attempts = emptyList(),
                selectionSummary = sandboxSelectionSummary(),
                metadata = command.metadata
            )
        }

        return paymentSavePort.save(payment)
    }

    fun simulateCancel(payment: Payment): Payment {
        log.info("Sandbox 취소 성공 시뮬레이션 — paymentId={}", payment.paymentId)
        val canceled = payment.copy(
            status = PaymentStatus.CANCELED,
            canceledAt = Instant.now(),
            cancelReason = "Sandbox 취소"
        )
        return paymentSavePort.save(canceled)
    }

    private fun sandboxSelectionSummary(): SelectionSummary = SelectionSummary(
        initialCandidates = emptyList(),
        filteredOutProviders = emptyList(),
        selectedPrimaryProvider = Provider.TOSS,
        selectedPrimaryReason = "Sandbox 시뮬레이션",
        fallbackReason = null,
        finalApprovedProvider = Provider.TOSS
    )
}
