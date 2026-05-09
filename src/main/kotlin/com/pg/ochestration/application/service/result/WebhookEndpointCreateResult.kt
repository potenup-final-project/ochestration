package com.pg.ochestration.application.service.result

import com.pg.ochestration.domain.model.WebhookEndpoint

data class WebhookEndpointCreateResult(
    val endpoint: WebhookEndpoint,
    val signingSecret: String
)
