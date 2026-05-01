package com.pg.ochestration.application.orchestration

import com.pg.ochestration.application.port.out.GatewayApproveCommand
import com.pg.ochestration.application.port.out.PaymentIdGeneratorPort
import com.pg.ochestration.application.port.out.PaymentProviderGateway
import com.pg.ochestration.domain.model.AttemptResult
import com.pg.ochestration.domain.model.FailureCategory
import com.pg.ochestration.domain.model.FallbackReasonCode
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentAttempt
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.SelectionSummary
import com.pg.ochestration.infrastructure.persistence.jpa.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

data class ApprovePaymentCommand(
    val merchantId: String,
    val orderId: String,
    val amount: Long,
    val currency: String,
    val idempotencyKey: String,
    val requestedAt: Instant,
    val preferredPrimaryProvider: Provider?,
    val metadata: Map<String, String>
)

@Component
class PgOrchestrator(
    private val paymentIdGenerator: PaymentIdGeneratorPort,
    private val providerSelectionPolicy: ProviderSelectionPolicy,
    private val gateways: List<PaymentProviderGateway>,
    private val paymentRepository: PaymentRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun approve(command: ApprovePaymentCommand): Payment {
        val paymentId = paymentIdGenerator.generate()
        val selection = providerSelectionPolicy.selectForApprove(command.merchantId, command.preferredPrimaryProvider)

        if (selection.candidates.isEmpty()) {
            logger.warn(
                "[Orchestrator] 가용 PG 없음 — paymentId={}, merchantId={}, filteredOut={}",
                paymentId, command.merchantId, selection.filteredOutProviders
            )
            return paymentRepository.save(
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
                    attempts = emptyList(),
                    selectionSummary = SelectionSummary.noProvider(
                        initialCandidates = selection.initialCandidates,
                        filteredOutProviders = selection.filteredOutProviders
                    ),
                    failureCode = "NO_PROVIDER_AVAILABLE",
                    failureCategory = FailureCategory.NON_RETRYABLE_BUSINESS,
                    failureMessage = "필터링 후 가용 PG가 없습니다",
                    metadata = command.metadata
                )
            )
        }

        val attempts = mutableListOf<PaymentAttempt>()
        var fallbackReasonCode: FallbackReasonCode? = null

        for ((index, provider) in selection.candidates.withIndex()) {
            logger.info(
                "[Orchestrator] paymentId={} attempt={} provider={} 시작",
                paymentId, index + 1, provider
            )

            val result = resolveGateway(provider).approve(
                GatewayApproveCommand(
                    merchantId = command.merchantId,
                    paymentId = paymentId,
                    orderId = command.orderId,
                    amount = command.amount,
                    currency = command.currency,
                    idempotencyKey = command.idempotencyKey,
                    requestedAt = command.requestedAt,
                    metadata = command.metadata
                )
            )

            if (result.success && result.providerTxId != null) {
                attempts += PaymentAttempt(
                    attemptNo = index + 1,
                    provider = provider,
                    result = AttemptResult.SUCCESS,
                    providerTxId = result.providerTxId,
                    attemptedAt = Instant.now()
                )

                val payment = Payment(
                    paymentId = paymentId,
                    merchantId = command.merchantId,
                    orderId = command.orderId,
                    amount = command.amount,
                    currency = command.currency,
                    idempotencyKey = command.idempotencyKey,
                    requestedAt = command.requestedAt,
                    status = PaymentStatus.APPROVED,
                    approvedProvider = provider,
                    providerTxId = result.providerTxId,
                    approvedAt = result.approvedAt,
                    attempts = attempts,
                    selectionSummary = SelectionSummary.approved(
                        selection = selection,
                        approvedProvider = provider,
                        fallbackReasonCode = fallbackReasonCode
                    ),
                    metadata = command.metadata + result.metadata
                )
                return paymentRepository.save(payment)
            }

            attempts += PaymentAttempt(
                attemptNo = index + 1,
                provider = provider,
                result = AttemptResult.FAIL,
                failureCategory = result.failure?.category,
                failureCode = result.failure?.code,
                failureMessage = result.failure?.message,
                attemptedAt = Instant.now()
            )

            if (result.failure?.category == FailureCategory.NON_RETRYABLE_BUSINESS) {
                logger.warn(
                    "[Orchestrator] NON_RETRYABLE_BUSINESS 실패로 폴백 중단 — paymentId={}, provider={}, code={}",
                    paymentId, provider, result.failure.code
                )
                fallbackReasonCode = FallbackReasonCode.NON_RETRYABLE_STOP
                break
            }

            val hasNext = index < selection.candidates.lastIndex
            if (hasNext) {
                val nextProvider = selection.candidates[index + 1]
                logger.info(
                    "[Orchestrator] 기술적 실패로 폴백 — paymentId={}, provider={} → {}",
                    paymentId, provider, nextProvider
                )
                fallbackReasonCode = FallbackReasonCode.PROVIDER_TECHNICAL_FAILURE
            } else {
                logger.warn(
                    "[Orchestrator] 모든 후보 소진 — paymentId={}, 마지막 provider={}",
                    paymentId, provider
                )
                fallbackReasonCode = FallbackReasonCode.ALL_PROVIDERS_EXHAUSTED
            }
        }

        val lastFailure = attempts.lastOrNull { it.result == AttemptResult.FAIL }
        return paymentRepository.save(
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
                attempts = attempts,
                selectionSummary = SelectionSummary.exhausted(
                    selection = selection,
                    fallbackReasonCode = requireNotNull(fallbackReasonCode) {
                        "fallbackReasonCode는 모든 후보 시도 후 반드시 설정되어야 합니다"
                    }
                ),
                failureCode = lastFailure?.failureCode,
                failureCategory = lastFailure?.failureCategory,
                failureMessage = lastFailure?.failureMessage,
                metadata = command.metadata
            )
        )
    }

    private fun resolveGateway(provider: Provider): PaymentProviderGateway =
        gateways.firstOrNull { it.supports(provider) }
            ?: error("provider=$provider 에 대응하는 PaymentProviderGateway를 찾을 수 없습니다")
}
