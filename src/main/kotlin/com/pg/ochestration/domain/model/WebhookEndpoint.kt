package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.WebhookEndpointInactiveException
import java.time.Instant

data class WebhookEndpoint(
    val endpointId: String,
    val merchantId: String,
    val url: String,
    val signingSecret: String,
    val status: WebhookEndpointStatus,
    val description: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun ensureActive() = status.ensureActive(endpointId)

    fun deactivate(now: Instant): WebhookEndpoint =
        copy(status = WebhookEndpointStatus.INACTIVE, updatedAt = now)

    fun update(url: String?, status: WebhookEndpointStatus?, description: String?, now: Instant): WebhookEndpoint =
        copy(
            url = url ?: this.url,
            status = status ?: this.status,
            description = description ?: this.description,
            updatedAt = now
        )
}

enum class WebhookEndpointStatus {
    ACTIVE,
    INACTIVE;

    fun isActive(): Boolean = this == ACTIVE

    fun ensureActive(endpointId: String) {
        if (!isActive()) throw WebhookEndpointInactiveException(endpointId)
    }
}
