package com.pg.ochestration.application.service.dto

import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.WebhookEventType
import java.time.Instant

data class WebhookPaymentEventPayload(
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
