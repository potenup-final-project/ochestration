package com.pg.ochestration.application.orchestration

import com.pg.ochestration.application.port.out.GatewayApproveCommand
import com.pg.ochestration.application.port.out.PaymentProviderGateway
import com.pg.ochestration.domain.model.AttemptResult
import com.pg.ochestration.domain.model.FailureCategory
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
    private val providerSelectionPolicy: ProviderSelectionPolicy,
    private val gateways: List<PaymentProviderGateway>,
    private val paymentRepository: PaymentRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun approve(command: ApprovePaymentCommand): Payment {
        val paymentId = paymentRepository.nextPaymentId()
        val selection = providerSelectionPolicy.selectForApprove(command.merchantId, command.preferredPrimaryProvider)

        if (selection.candidates.isEmpty()) {
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
                    selectionSummary = SelectionSummary(
                        initialCandidates = selection.initialCandidates,
                        filteredOutProviders = selection.filteredOutProviders,
                        selectedPrimaryProvider = null,
                        selectedPrimaryReason = selection.selectedPrimaryReason,
                        fallbackReason = "No provider available after filtering",
                        finalApprovedProvider = null
                    ),
                    failureCode = "NO_PROVIDER_AVAILABLE",
                    failureCategory = FailureCategory.NON_RETRYABLE_BUSINESS,
                    failureMessage = "No provider available after filtering",
                    metadata = command.metadata
                )
            )
        }

        val attempts = mutableListOf<PaymentAttempt>()
        var fallbackReason: String? = null

        for ((index, provider) in selection.candidates.withIndex()) {
            logger.info("[Orchestrator] paymentId={} attempt={} provider={} start", paymentId, index + 1, provider)

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
                    selectionSummary = SelectionSummary(
                        initialCandidates = selection.initialCandidates,
                        filteredOutProviders = selection.filteredOutProviders,
                        selectedPrimaryProvider = selection.selectedPrimaryProvider,
                        selectedPrimaryReason = selection.selectedPrimaryReason,
                        fallbackReason = fallbackReason,
                        finalApprovedProvider = provider
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

            val hasNext = index < selection.candidates.lastIndex
            if (result.failure?.category == FailureCategory.NON_RETRYABLE_BUSINESS) {
                fallbackReason = "No fallback because failure category is NON_RETRYABLE_BUSINESS"
                break
            }

            if (hasNext) {
                val nextProvider = selection.candidates[index + 1]
                fallbackReason = "${provider.name} failed with ${result.failure?.category}(${result.failure?.code}), fallback to ${nextProvider.name}"
            } else {
                fallbackReason = "No more available providers after retryable failures"
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
                selectionSummary = SelectionSummary(
                    initialCandidates = selection.initialCandidates,
                    filteredOutProviders = selection.filteredOutProviders,
                    selectedPrimaryProvider = selection.selectedPrimaryProvider,
                    selectedPrimaryReason = selection.selectedPrimaryReason,
                    fallbackReason = fallbackReason,
                    finalApprovedProvider = null
                ),
                failureCode = lastFailure?.failureCode,
                failureCategory = lastFailure?.failureCategory,
                failureMessage = lastFailure?.failureMessage,
                metadata = command.metadata
            )
        )
    }

    private fun resolveGateway(provider: Provider): PaymentProviderGateway {
        return gateways.firstOrNull { it.supports(provider) }
            ?: error("No PaymentProviderGateway found for provider=$provider")
    }
}
