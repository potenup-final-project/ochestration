package com.pg.ochestration.presentation.web.controller.request

import com.pg.ochestration.application.service.command.WebhookEndpointUpdateCommand
import com.pg.ochestration.domain.model.WebhookEndpointStatus

data class CreateWebhookEndpointRequest(
    val url: String,
    val description: String? = null
)

data class UpdateWebhookEndpointRequest(
    val url: String? = null,
    val status: WebhookEndpointStatus? = null,
    val description: String? = null
) {
    fun toCommand(merchantId: String, endpointId: String): WebhookEndpointUpdateCommand =
        WebhookEndpointUpdateCommand(
            merchantId = merchantId,
            endpointId = endpointId,
            url = url,
            status = status,
            description = description
        )
}
