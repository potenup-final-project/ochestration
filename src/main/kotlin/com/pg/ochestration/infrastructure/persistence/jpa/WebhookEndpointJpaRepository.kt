package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.domain.model.WebhookEndpointStatus
import com.pg.ochestration.infrastructure.persistence.jpa.entity.WebhookEndpointJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface WebhookEndpointJpaRepository : JpaRepository<WebhookEndpointJpaEntity, String> {
    fun findAllByMerchantIdAndStatus(merchantId: String, status: WebhookEndpointStatus): List<WebhookEndpointJpaEntity>
    fun countByMerchantId(merchantId: String): Int
}
