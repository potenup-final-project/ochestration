package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.application.port.out.WebhookDeliveryRepository
import com.pg.ochestration.domain.model.WebhookDelivery
import com.pg.ochestration.infrastructure.persistence.jpa.entity.WebhookDeliveryJpaEntity
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional(readOnly = true)
class WebhookDeliveryAdapter(
    private val jpaRepository: WebhookDeliveryJpaRepository
) : WebhookDeliveryRepository {

    @Transactional
    override fun save(delivery: WebhookDelivery): WebhookDelivery =
        jpaRepository.save(WebhookDeliveryJpaEntity.from(delivery)).toDomain()

    @Transactional
    override fun saveAll(deliveries: List<WebhookDelivery>): List<WebhookDelivery> =
        jpaRepository.saveAll(deliveries.map { WebhookDeliveryJpaEntity.from(it) })
            .map { it.toDomain() }

    override fun findDueForDispatch(now: Instant, limit: Int): List<WebhookDelivery> {
        require(limit > 0) { "웹훅 dispatch 조회 limit은 1 이상이어야 합니다: limit=$limit" }
        return jpaRepository.findDueForDispatch(now, PageRequest.of(0, limit))
            .map { it.toDomain() }
    }
}
