package com.pg.ochestration.infrastructure.persistence.jpa.entity

import com.pg.ochestration.infrastructure.idempotency.IdempotencyOperation
import com.pg.ochestration.infrastructure.idempotency.IdempotencyStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "idempotency_records")
class IdempotencyRecordJpaEntity(
    @Id
    @Column(name = "record_id", nullable = false)
    val recordId: String,

    @Column(name = "redis_key", nullable = false, length = 512)
    val redisKey: String,

    @Column(name = "merchant_id", nullable = false, length = 100)
    val merchantId: String,

    @Column(name = "idempotency_key", nullable = false)
    val idempotencyKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 20)
    val operation: IdempotencyOperation,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: IdempotencyStatus,

    @Column(name = "http_status", nullable = true)
    var httpStatus: Int? = null,

    @Column(name = "response_body", columnDefinition = "MEDIUMTEXT", nullable = true)
    var responseBody: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "completed_at", nullable = true)
    var completedAt: Instant? = null
) {
    fun complete(httpStatus: Int, responseBody: String) {
        this.status = IdempotencyStatus.COMPLETED
        this.httpStatus = httpStatus
        this.responseBody = responseBody
        this.completedAt = Instant.now()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IdempotencyRecordJpaEntity) return false
        return recordId == other.recordId
    }

    override fun hashCode(): Int = recordId.hashCode()
}
