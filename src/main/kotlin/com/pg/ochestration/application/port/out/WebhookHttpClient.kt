package com.pg.ochestration.application.port.out

import com.pg.ochestration.domain.model.WebhookDelivery
import com.pg.ochestration.domain.model.WebhookEndpoint

interface WebhookHttpClient {
    fun post(endpoint: WebhookEndpoint, delivery: WebhookDelivery, signature: String): WebhookHttpResponse
}

data class WebhookHttpResponse(
    val statusCode: Int
) {
    fun isSuccessful(): Boolean = statusCode in 200..299
}
