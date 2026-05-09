package com.pg.ochestration.presentation.web.controller.request

import com.pg.ochestration.application.service.command.ProviderConnectCommand
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderHealthStatus

data class ProviderConnectRequest(
    val provider: Provider,
    val displayName: String,
    val apiKey: String
) {
    fun toCommand(merchantId: String): ProviderConnectCommand =
        ProviderConnectCommand(
            merchantId = merchantId,
            provider = provider,
            displayName = displayName,
            apiKey = apiKey
        )
}

data class ProviderHealthUpdateRequest(
    val provider: Provider,
    val health: ProviderHealthStatus
)
