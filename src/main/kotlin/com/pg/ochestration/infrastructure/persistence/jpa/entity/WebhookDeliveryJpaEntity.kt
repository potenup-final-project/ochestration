package com.pg.ochestration.infrastructure.persistence.jpa.entity

import com.pg.ochestration.domain.model.WebhookDelivery
import com.pg.ochestration.domain.model.WebhookDeliveryStatus
import com.pg.ochestration.domain.model.WebhookEventType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "webhook_deliveries",
    indexes = [
        Index(name = "idx_webhook_deliveries_status_retry", columnList = "status, next_retry_at"),
        Index(name = "idx_webhook_deliveries_merchant_created", columnList = "merchant_id, created_at"),
        Index(name = "idx_webhook_deliveries_payment", columnList = "payment_id")
    ]
)
class WebhookDeliveryJpaEntity(
    @Id
    @Column(name = "delivery_id", length = 36, nullable = false)
    val deliveryId: String,

    @Column(name = "endpoint_id", length = 36, nullable = false)
    val endpointId: String,

    @Column(name = "merchant_id", length = 100, nullable = false)
    val merchantId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false)
    val eventType: WebhookEventType,

    @Column(name = "event_id", length = 36, nullable = false)
    val eventId: String,

    @Column(name = "payment_id", length = 100, nullable = false)
    val paymentId: String,

    @Column(name = "payload", columnDefinition = "MEDIUMTEXT", nullable = false)
    val payload: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    val status: WebhookDeliveryStatus,

    @Column(name = "attempt_count", nullable = false)
    val attemptCount: Int,

    @Column(name = "max_attempts", nullable = false)
    val maxAttempts: Int,

    @Column(name = "next_retry_at")
    val nextRetryAt: Instant?,

    @Column(name = "last_attempted_at")
    val lastAttemptedAt: Instant?,

    @Column(name = "last_response_code")
    val lastResponseCode: Int?,

    @Column(name = "last_error", length = 1000)
    val lastError: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant
) {
    fun toDomain(): WebhookDelivery = WebhookDelivery(
        deliveryId = deliveryId,
        endpointId = endpointId,
        merchantId = merchantId,
        eventType = eventType,
        eventId = eventId,
        paymentId = paymentId,
        payload = payload,
        status = status,
        attemptCount = attemptCount,
        maxAttempts = maxAttempts,
        nextRetryAt = nextRetryAt,
        lastAttemptedAt = lastAttemptedAt,
        lastResponseCode = lastResponseCode,
        lastError = lastError,
        createdAt = createdAt
    )

    companion object {
        fun from(domain: WebhookDelivery): WebhookDeliveryJpaEntity = WebhookDeliveryJpaEntity(
            deliveryId = domain.deliveryId,
            endpointId = domain.endpointId,
            merchantId = domain.merchantId,
            eventType = domain.eventType,
            eventId = domain.eventId,
            paymentId = domain.paymentId,
            payload = domain.payload,
            status = domain.status,
            attemptCount = domain.attemptCount,
            maxAttempts = domain.maxAttempts,
            nextRetryAt = domain.nextRetryAt,
            lastAttemptedAt = domain.lastAttemptedAt,
            lastResponseCode = domain.lastResponseCode,
            lastError = domain.lastError,
            createdAt = domain.createdAt
        )
    }
}
