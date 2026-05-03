package com.pg.ochestration.domain.model

enum class WebhookEventType(
    val value: String
) {
    PAYMENT_APPROVED("PAYMENT.APPROVED"),
    PAYMENT_FAILED("PAYMENT.FAILED"),
    PAYMENT_CANCELED("PAYMENT.CANCELED")
}
