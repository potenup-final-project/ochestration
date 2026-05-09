package com.pg.ochestration.application.service.command

import com.pg.ochestration.domain.model.WebhookEndpointStatus

data class WebhookEndpointUpdateCommand(
    val merchantId: String,
    val endpointId: String,
    val url: String?,
    val status: WebhookEndpointStatus?,
    val description: String?
)
