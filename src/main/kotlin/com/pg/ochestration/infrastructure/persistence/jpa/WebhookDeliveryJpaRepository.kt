package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.infrastructure.persistence.jpa.entity.WebhookDeliveryJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface WebhookDeliveryJpaRepository : JpaRepository<WebhookDeliveryJpaEntity, String> {

    @Query("""
        SELECT d
        FROM WebhookDeliveryJpaEntity d
        WHERE d.status = com.pg.ochestration.domain.model.WebhookDeliveryStatus.PENDING
           OR (
                d.status = com.pg.ochestration.domain.model.WebhookDeliveryStatus.FAILED
                AND d.nextRetryAt IS NOT NULL
                AND d.nextRetryAt <= :now
           )
        ORDER BY d.createdAt ASC, d.deliveryId ASC
    """)
    fun findDueForDispatch(now: Instant, pageable: Pageable): List<WebhookDeliveryJpaEntity>
}
