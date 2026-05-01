package com.pg.ochestration.infrastructure.persistence.jpa.entity

import com.pg.ochestration.domain.model.AttemptResult
import com.pg.ochestration.domain.model.FailureCategory
import com.pg.ochestration.domain.model.Provider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "payment_attempts")
class PaymentAttemptJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    val payment: PaymentJpaEntity,

    @Column(name = "attempt_no", nullable = false)
    val attemptNo: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    val provider: Provider,

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 50)
    val attemptResult: AttemptResult,

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", nullable = true, length = 50)
    val failureCategory: FailureCategory? = null,

    @Column(name = "failure_code", nullable = true, length = 100)
    val failureCode: String? = null,

    @Column(name = "failure_message", nullable = true, length = 500)
    val failureMessage: String? = null,

    @Column(name = "provider_tx_id", nullable = true)
    val providerTxId: String? = null,

    @Column(name = "attempted_at", nullable = false)
    val attemptedAt: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaymentAttemptJpaEntity) return false
        return id != 0L && id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
