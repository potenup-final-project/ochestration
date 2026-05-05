package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.application.port.out.WebhookEndpointRepository
import com.pg.ochestration.domain.model.WebhookEndpoint
import com.pg.ochestration.domain.model.WebhookEndpointStatus
import com.pg.ochestration.infrastructure.persistence.jpa.entity.WebhookEndpointJpaEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional(readOnly = true)
class WebhookEndpointAdapter(
    private val jpaRepository: WebhookEndpointJpaRepository
) : WebhookEndpointRepository {

    @Transactional
    override fun save(endpoint: WebhookEndpoint): WebhookEndpoint =
        jpaRepository.save(WebhookEndpointJpaEntity.from(endpoint)).toDomain()

    override fun findById(endpointId: String): WebhookEndpoint? =
        jpaRepository.findById(endpointId).orElse(null)?.toDomain()

    override fun findAllByMerchantId(merchantId: String): List<WebhookEndpoint> =
        jpaRepository.findAllByMerchantId(merchantId).map { it.toDomain() }

    override fun findActiveByMerchantId(merchantId: String): List<WebhookEndpoint> =
        jpaRepository.findAllByMerchantIdAndStatus(merchantId, WebhookEndpointStatus.ACTIVE)
            .map { it.toDomain() }

    override fun countByMerchantId(merchantId: String): Int =
        jpaRepository.countByMerchantId(merchantId)

    @Transactional
    override fun deactivate(endpointId: String, now: Instant): WebhookEndpoint? {
        val endpoint = findById(endpointId) ?: return null
        return save(endpoint.deactivate(now))
    }
}
