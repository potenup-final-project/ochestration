package com.pg.ochestration.infrastructure.persistence.jpa.entity

import com.pg.ochestration.domain.model.FailureCategory
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.time.LocalDateTime

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener::class)
class PaymentJpaEntity(
    @Id
    @Column(name = "payment_id", nullable = false)
    val paymentId: String,

    @Column(name = "merchant_id", nullable = false)
    val merchantId: String,

    @Column(name = "order_id", nullable = false)
    val orderId: String,

    @Column(name = "amount", nullable = false)
    val amount: Long,

    @Column(name = "currency", nullable = false, length = 10)
    val currency: String,

    @Column(name = "idempotency_key", nullable = false)
    val idempotencyKey: String,

    @Column(name = "requested_at", nullable = false)
    val requestedAt: Instant,

    initialStatus: PaymentStatus,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
) {
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    final var status: PaymentStatus = initialStatus
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "approved_provider", nullable = true, length = 50)
    final var approvedProvider: Provider? = null
        private set

    @Column(name = "provider_tx_id", nullable = true)
    final var providerTxId: String? = null
        private set

    @Column(name = "approved_at", nullable = true)
    final var approvedAt: Instant? = null
        private set

    @Column(name = "canceled_at", nullable = true)
    final var canceledAt: Instant? = null
        private set

    @Column(name = "failure_code", nullable = true, length = 100)
    final var failureCode: String? = null
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", nullable = true, length = 50)
    final var failureCategory: FailureCategory? = null
        private set

    @Column(name = "failure_message", nullable = true, length = 500)
    final var failureMessage: String? = null
        private set

    @Column(name = "metadata", columnDefinition = "TEXT", nullable = true)
    final var metadata: String? = null
        private set

    @Column(name = "cancel_reason", nullable = true, length = 500)
    final var cancelReason: String? = null
        private set

    @OneToMany(
        mappedBy = "payment",
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        fetch = FetchType.LAZY,
        orphanRemoval = true
    )
    private val _attempts: MutableList<PaymentAttemptJpaEntity> = mutableListOf()

    val attempts: List<PaymentAttemptJpaEntity> get() = _attempts

    fun syncAttempts(newAttempts: List<PaymentAttemptJpaEntity>) {
        _attempts.clear()
        _attempts.addAll(newAttempts)
    }

    fun markApproved(
        provider: Provider,
        providerTxId: String,
        approvedAt: Instant,
        metadata: String?
    ) {
        this.status = PaymentStatus.APPROVED
        this.approvedProvider = provider
        this.providerTxId = providerTxId
        this.approvedAt = approvedAt
        this.metadata = metadata
    }

    fun markFailed(
        failureCode: String?,
        failureCategory: FailureCategory?,
        failureMessage: String?,
        metadata: String?
    ) {
        this.status = PaymentStatus.FAILED
        this.failureCode = failureCode
        this.failureCategory = failureCategory
        this.failureMessage = failureMessage
        this.metadata = metadata
    }

    fun markCanceled(
        canceledAt: Instant,
        reason: String,
        failureCode: String?,
        failureCategory: FailureCategory?,
        failureMessage: String?,
        metadata: String?
    ) {
        this.status = PaymentStatus.CANCELED
        this.canceledAt = canceledAt
        this.cancelReason = reason
        this.failureCode = failureCode
        this.failureCategory = failureCategory
        this.failureMessage = failureMessage
        this.metadata = metadata
    }

    fun updateMetadata(metadata: String?) {
        this.metadata = metadata
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaymentJpaEntity) return false
        return paymentId == other.paymentId
    }

    override fun hashCode(): Int = paymentId.hashCode()
}
