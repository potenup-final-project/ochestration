package com.pg.ochestration.application.port.out

import com.pg.ochestration.domain.model.WebhookEndpoint
import java.time.Instant

interface WebhookEndpointRepository {
    fun save(endpoint: WebhookEndpoint): WebhookEndpoint
    fun findById(endpointId: String): WebhookEndpoint?
    fun findAllByMerchantId(merchantId: String): List<WebhookEndpoint>
    fun findActiveByMerchantId(merchantId: String): List<WebhookEndpoint>
    fun countByMerchantId(merchantId: String): Int
    fun deactivate(endpointId: String, now: Instant): WebhookEndpoint?
}
