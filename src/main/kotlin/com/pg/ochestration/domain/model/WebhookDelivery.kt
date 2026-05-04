package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.WebhookDeliveryNotRetryableException
import java.time.Instant

data class WebhookDelivery(
    val deliveryId: String,
    val endpointId: String,
    val merchantId: String,
    val eventType: WebhookEventType,
    val eventId: String,
    val paymentId: String,
    val payload: String,
    val status: WebhookDeliveryStatus,
    val attemptCount: Int,
    val maxAttempts: Int,
    val nextRetryAt: Instant?,
    val lastAttemptedAt: Instant?,
    val lastResponseCode: Int?,
    val lastError: String?,
    val createdAt: Instant
) {
    fun markSent(responseCode: Int, now: Instant = Instant.now()): WebhookDelivery {
        status.ensureRetryable(deliveryId)
        return copy(
            status = WebhookDeliveryStatus.SENT,
            attemptCount = attemptCount + 1,
            nextRetryAt = null,
            lastAttemptedAt = now,
            lastResponseCode = responseCode,
            lastError = null
        )
    }

    fun markFailed(error: String, responseCode: Int?, now: Instant = Instant.now()): WebhookDelivery {
        status.ensureRetryable(deliveryId)
        val nextAttemptCount = attemptCount + 1
        return if (nextAttemptCount >= maxAttempts) {
            markDead(error = error, responseCode = responseCode, now = now, attemptCount = nextAttemptCount)
        } else {
            copy(
                status = WebhookDeliveryStatus.FAILED,
                attemptCount = nextAttemptCount,
                nextRetryAt = calculateNextRetryAt(nextAttemptCount, now),
                lastAttemptedAt = now,
                lastResponseCode = responseCode,
                lastError = error
            )
        }
    }

    fun markDead(error: String, responseCode: Int?, now: Instant = Instant.now()): WebhookDelivery {
        status.ensureRetryable(deliveryId)
        return markDead(error = error, responseCode = responseCode, now = now, attemptCount = attemptCount + 1)
    }

    private fun markDead(error: String, responseCode: Int?, now: Instant, attemptCount: Int): WebhookDelivery =
        copy(
            status = WebhookDeliveryStatus.DEAD,
            attemptCount = attemptCount,
            nextRetryAt = null,
            lastAttemptedAt = now,
            lastResponseCode = responseCode,
            lastError = error
        )

    private fun calculateNextRetryAt(attemptCount: Int, now: Instant): Instant =
        now.plusSeconds(1L shl (attemptCount - 1))
}

enum class WebhookDeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    DEAD;

    fun isTerminal(): Boolean = this == SENT || this == DEAD

    fun ensureRetryable(deliveryId: String) {
        if (isTerminal()) throw WebhookDeliveryNotRetryableException(deliveryId, name)
    }
}
