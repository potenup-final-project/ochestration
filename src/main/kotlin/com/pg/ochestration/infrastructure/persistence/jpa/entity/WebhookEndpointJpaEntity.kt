package com.pg.ochestration.infrastructure.persistence.jpa.entity

import com.pg.ochestration.domain.model.WebhookEndpoint
import com.pg.ochestration.domain.model.WebhookEndpointStatus
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
    name = "webhook_endpoints",
    indexes = [
        Index(name = "idx_webhook_endpoints_merchant_status", columnList = "merchant_id, status")
    ]
)
class WebhookEndpointJpaEntity(
    @Id
    @Column(name = "endpoint_id", length = 36, nullable = false)
    val endpointId: String,

    @Column(name = "merchant_id", length = 100, nullable = false)
    val merchantId: String,

    @Column(name = "url", length = 2048, nullable = false)
    val url: String,

    @Column(name = "signing_secret", length = 255, nullable = false)
    val signingSecret: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    val status: WebhookEndpointStatus,

    @Column(name = "description", length = 255)
    val description: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant
) {
    fun toDomain(): WebhookEndpoint = WebhookEndpoint(
        endpointId = endpointId,
        merchantId = merchantId,
        url = url,
        signingSecret = signingSecret,
        status = status,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(domain: WebhookEndpoint): WebhookEndpointJpaEntity = WebhookEndpointJpaEntity(
            endpointId = domain.endpointId,
            merchantId = domain.merchantId,
            url = domain.url,
            signingSecret = domain.signingSecret,
            status = domain.status,
            description = domain.description,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
