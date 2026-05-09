package com.pg.ochestration.presentation.web.controller.response

import com.pg.ochestration.application.service.result.WebhookEndpointCreateResult
import com.pg.ochestration.domain.model.WebhookEndpoint
import com.pg.ochestration.domain.model.WebhookEndpointStatus
import java.time.Instant

data class WebhookEndpointCreateResponse(
    val endpointId: String,
    val merchantId: String,
    val url: String,
    val status: WebhookEndpointStatus,
    val description: String?,
    val signingSecret: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(result: WebhookEndpointCreateResult): WebhookEndpointCreateResponse =
            WebhookEndpointCreateResponse(
                endpointId = result.endpoint.endpointId,
                merchantId = result.endpoint.merchantId,
                url = result.endpoint.url,
                status = result.endpoint.status,
                description = result.endpoint.description,
                signingSecret = result.signingSecret,
                createdAt = result.endpoint.createdAt,
                updatedAt = result.endpoint.updatedAt
            )
    }
}

data class WebhookEndpointView(
    val endpointId: String,
    val merchantId: String,
    val url: String,
    val status: WebhookEndpointStatus,
    val description: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(endpoint: WebhookEndpoint): WebhookEndpointView =
            WebhookEndpointView(
                endpointId = endpoint.endpointId,
                merchantId = endpoint.merchantId,
                url = endpoint.url,
                status = endpoint.status,
                description = endpoint.description,
                createdAt = endpoint.createdAt,
                updatedAt = endpoint.updatedAt
            )
    }
}
