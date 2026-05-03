package com.pg.ochestration.application.port.out

import com.pg.ochestration.domain.model.WebhookDelivery
import java.time.Instant

interface WebhookDeliveryRepository {
    fun save(delivery: WebhookDelivery): WebhookDelivery
    fun saveAll(deliveries: List<WebhookDelivery>): List<WebhookDelivery>
    fun findDueForDispatch(now: Instant, limit: Int): List<WebhookDelivery>
}
