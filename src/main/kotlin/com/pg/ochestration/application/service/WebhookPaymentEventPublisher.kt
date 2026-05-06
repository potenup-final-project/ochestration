package com.pg.ochestration.application.service

import com.pg.ochestration.application.port.out.WebhookDeliveryRepository
import com.pg.ochestration.application.port.out.WebhookEndpointRepository
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.WebhookDelivery
import com.pg.ochestration.domain.model.WebhookDeliveryStatus
import com.pg.ochestration.domain.model.WebhookEventType
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Service
class WebhookPaymentEventPublisher(
    private val webhookEndpointRepository: WebhookEndpointRepository,
    private val webhookDeliveryRepository: WebhookDeliveryRepository,
    private val objectMapper: ObjectMapper
) {

    fun publish(payment: Payment, eventType: WebhookEventType): List<WebhookDelivery> {
        val endpoints = webhookEndpointRepository.findActiveByMerchantId(payment.merchantId)
        if (endpoints.isEmpty()) return emptyList()

        val eventId = UUID.randomUUID().toString()
        val occurredAt = Instant.now()
        val payload = objectMapper.writeValueAsString(WebhookPaymentEventPayload.from(payment, eventType, eventId, occurredAt))
        val deliveries = endpoints.map { endpoint ->
            WebhookDelivery(
                deliveryId = UUID.randomUUID().toString(),
                endpointId = endpoint.endpointId,
                merchantId = payment.merchantId,
                eventType = eventType,
                eventId = eventId,
                paymentId = payment.paymentId,
                payload = payload,
                status = WebhookDeliveryStatus.PENDING,
                attemptCount = 0,
                maxAttempts = MAX_ATTEMPTS,
                nextRetryAt = null,
                lastAttemptedAt = null,
                lastResponseCode = null,
                lastError = null,
                createdAt = occurredAt
            )
        }

        return webhookDeliveryRepository.saveAll(deliveries)
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

private data class WebhookPaymentEventPayload(
    val eventType: String,
    val eventId: String,
    val paymentId: String,
    val merchantId: String,
    val amount: Long,
    val currency: String,
    val status: PaymentStatus,
    val occurredAt: Instant
) {
    companion object {
        fun from(
            payment: Payment,
            eventType: WebhookEventType,
            eventId: String,
            occurredAt: Instant
        ): WebhookPaymentEventPayload =
            WebhookPaymentEventPayload(
                eventType = eventType.value,
                eventId = eventId,
                paymentId = payment.paymentId,
                merchantId = payment.merchantId,
                amount = payment.amount,
                currency = payment.currency,
                status = payment.status,
                occurredAt = occurredAt
            )
    }
}
