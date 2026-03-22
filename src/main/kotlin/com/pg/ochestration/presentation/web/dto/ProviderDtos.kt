package com.pg.ochestration.presentation.web.dto

import com.pg.ochestration.domain.model.ConnectionStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderHealthStatus

data class ProviderConnectRequest(
    val provider: Provider,
    val displayName: String,
    val apiKey: String
)

data class ProviderConnectionResponse(
    val providerConnectionId: String,
    val merchantId: String,
    val provider: Provider,
    val displayName: String,
    val status: ConnectionStatus
)

data class ProviderDisconnectResponse(
    val provider: Provider,
    val merchantId: String,
    val status: ConnectionStatus
)

data class ProviderHealthUpdateRequest(
    val provider: Provider,
    val health: ProviderHealthStatus
)

data class ProviderHealthResponse(
    val provider: Provider,
    val health: ProviderHealthStatus
)
