package com.pg.ochestration.infrastructure.persistence.jpa

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.pg.ochestration.domain.model.FilteredOutProvider
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentAttempt
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.SelectionSummary
import com.pg.ochestration.infrastructure.persistence.jpa.entity.PaymentAttemptJpaEntity
import com.pg.ochestration.infrastructure.persistence.jpa.entity.PaymentJpaEntity
import com.pg.ochestration.infrastructure.persistence.jpa.entity.SelectionFilteredOutProviderJpaEntity
import com.pg.ochestration.infrastructure.persistence.jpa.entity.SelectionInitialCandidateJpaEntity
import com.pg.ochestration.infrastructure.persistence.jpa.entity.SelectionSummaryJpaEntity
import com.pg.ochestration.application.port.out.PaymentSavePort
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class PaymentRepository(
    private val jpaRepository: PaymentJpaRepository,
    private val selectionSummaryJpaRepository: SelectionSummaryJpaRepository,
    private val queryDslRepository: PaymentQueryDslRepository,
    private val objectMapper: ObjectMapper
) : PaymentSavePort {
    fun nextPaymentId(): String = UUID.randomUUID().toString()

    @Transactional
    override fun save(payment: Payment): Payment {
        val entity = toPaymentEntity(payment)
        val saved = jpaRepository.save(entity)
        val summary = toSummaryEntity(saved, payment)
        val savedSummary = selectionSummaryJpaRepository.save(summary)
        return toDomain(saved, savedSummary)
    }

    override fun findById(paymentId: String): Payment? {
        val entity = queryDslRepository.findByPaymentId(paymentId) ?: return null
        val summary = selectionSummaryJpaRepository.findByPaymentPaymentId(paymentId)
        return toDomain(entity, summary)
    }

    private fun toPaymentEntity(payment: Payment): PaymentJpaEntity {
        val entity = jpaRepository.findById(payment.paymentId).orElse(null) ?: PaymentJpaEntity(
            paymentId = payment.paymentId,
            merchantId = payment.merchantId,
            orderId = payment.orderId,
            amount = payment.amount,
            currency = payment.currency,
            idempotencyKey = payment.idempotencyKey,
            requestedAt = payment.requestedAt,
            initialStatus = payment.status
        )

        when (payment.status) {
            PaymentStatus.APPROVED -> entity.markApproved(
                provider = requireNotNull(payment.approvedProvider) {
                    "approvedProvider must not be null when status is APPROVED"
                },
                providerTxId = requireNotNull(payment.providerTxId) {
                    "providerTxId must not be null when status is APPROVED"
                },
                approvedAt = requireNotNull(payment.approvedAt) {
                    "approvedAt must not be null when status is APPROVED"
                },
                metadata = serializeMetadata(payment.metadata)
            )
            PaymentStatus.FAILED -> entity.markFailed(
                failureCode = payment.failureCode,
                failureCategory = payment.failureCategory,
                failureMessage = payment.failureMessage,
                metadata = serializeMetadata(payment.metadata)
            )
            PaymentStatus.CANCELED -> entity.markCanceled(
                canceledAt = requireNotNull(payment.canceledAt) {
                    "canceledAt must not be null when status is CANCELED"
                },
                reason = requireNotNull(payment.cancelReason) {
                    "cancelReason must not be null when status is CANCELED"
                },
                failureCode = payment.failureCode,
                failureCategory = payment.failureCategory,
                failureMessage = payment.failureMessage,
                metadata = serializeMetadata(payment.metadata)
            )
            PaymentStatus.READY -> entity.updateMetadata(serializeMetadata(payment.metadata))
        }

        val newAttempts = payment.attempts.map { attempt ->
            PaymentAttemptJpaEntity(
                payment = entity,
                attemptNo = attempt.attemptNo,
                provider = attempt.provider,
                attemptResult = attempt.result,
                failureCategory = attempt.failureCategory,
                failureCode = attempt.failureCode,
                failureMessage = attempt.failureMessage,
                providerTxId = attempt.providerTxId,
                attemptedAt = attempt.attemptedAt
            )
        }
        entity.syncAttempts(newAttempts)

        return entity
    }

    private fun toSummaryEntity(entity: PaymentJpaEntity, payment: Payment): SelectionSummaryJpaEntity {
        val summary = selectionSummaryJpaRepository.findByPaymentPaymentId(entity.paymentId)
            ?: SelectionSummaryJpaEntity(
                payment = entity,
                initialSelectedPrimaryProvider = payment.selectionSummary.selectedPrimaryProvider,
                initialSelectedPrimaryReason = payment.selectionSummary.selectedPrimaryReason,
                initialFallbackReason = payment.selectionSummary.fallbackReason,
                initialFinalApprovedProvider = payment.selectionSummary.finalApprovedProvider
            )

        val newInitialCandidates = payment.selectionSummary.initialCandidates.mapIndexed { index, provider ->
            SelectionInitialCandidateJpaEntity(selectionSummary = summary, provider = provider, sortOrder = index)
        }
        val newFilteredOutProviders = payment.selectionSummary.filteredOutProviders.map { filteredOut ->
            SelectionFilteredOutProviderJpaEntity(selectionSummary = summary, provider = filteredOut.provider, reason = filteredOut.reason)
        }
        summary.syncSelectionData(
            selectedPrimaryProvider = payment.selectionSummary.selectedPrimaryProvider,
            selectedPrimaryReason = payment.selectionSummary.selectedPrimaryReason,
            fallbackReason = payment.selectionSummary.fallbackReason,
            finalApprovedProvider = payment.selectionSummary.finalApprovedProvider,
            initialCandidates = newInitialCandidates,
            filteredOutProviders = newFilteredOutProviders
        )

        return summary
    }

    private fun toDomain(entity: PaymentJpaEntity, summary: SelectionSummaryJpaEntity?): Payment {
        val selectionSummary = SelectionSummary(
            initialCandidates = summary?.initialCandidates
                ?.sortedBy { it.sortOrder }
                ?.map { it.provider }
                ?: emptyList(),
            filteredOutProviders = summary?.filteredOutProviders
                ?.map { FilteredOutProvider(it.provider, it.reason) }
                ?: emptyList(),
            selectedPrimaryProvider = summary?.selectedPrimaryProvider,
            selectedPrimaryReason = summary?.selectedPrimaryReason ?: "",
            fallbackReason = summary?.fallbackReason,
            finalApprovedProvider = summary?.finalApprovedProvider
        )

        return Payment(
            paymentId = entity.paymentId,
            merchantId = entity.merchantId,
            orderId = entity.orderId,
            amount = entity.amount,
            currency = entity.currency,
            idempotencyKey = entity.idempotencyKey,
            requestedAt = entity.requestedAt,
            status = entity.status,
            approvedProvider = entity.approvedProvider,
            providerTxId = entity.providerTxId,
            approvedAt = entity.approvedAt,
            canceledAt = entity.canceledAt,
            failureCode = entity.failureCode,
            failureCategory = entity.failureCategory,
            failureMessage = entity.failureMessage,
            attempts = entity.attempts.map { attempt ->
                PaymentAttempt(
                    attemptNo = attempt.attemptNo,
                    provider = attempt.provider,
                    result = attempt.attemptResult,
                    failureCategory = attempt.failureCategory,
                    failureCode = attempt.failureCode,
                    failureMessage = attempt.failureMessage,
                    providerTxId = attempt.providerTxId,
                    attemptedAt = attempt.attemptedAt
                )
            },
            selectionSummary = selectionSummary,
            metadata = deserializeMetadata(entity.metadata),
            cancelReason = entity.cancelReason
        )
    }

    private fun serializeMetadata(metadata: Map<String, String>): String? {
        if (metadata.isEmpty()) return null
        return objectMapper.writeValueAsString(metadata)
    }

    private fun deserializeMetadata(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return objectMapper.readValue(json, object : TypeReference<Map<String, String>>() {})
    }
}
