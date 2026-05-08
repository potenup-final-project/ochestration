package com.pg.ochestration.presentation.web.controller.response

import com.pg.ochestration.domain.model.ConnectionStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderConnection
import com.pg.ochestration.domain.model.ProviderHealthStatus

data class ProviderConnectionResponse(
    val providerConnectionId: String,
    val merchantId: String,
    val provider: Provider,
    val displayName: String,
    val status: ConnectionStatus
) {
    companion object {
        fun from(connection: ProviderConnection): ProviderConnectionResponse =
            ProviderConnectionResponse(
                providerConnectionId = connection.providerConnectionId,
                merchantId = connection.merchantId,
                provider = connection.provider,
                displayName = connection.displayName,
                status = connection.status
            )
    }
}

data class ProviderDisconnectResponse(
    val provider: Provider,
    val merchantId: String,
    val status: ConnectionStatus
) {
    companion object {
        fun from(connection: ProviderConnection): ProviderDisconnectResponse =
            ProviderDisconnectResponse(
                provider = connection.provider,
                merchantId = connection.merchantId,
                status = connection.status
            )
    }
}

data class ProviderHealthResponse(
    val provider: Provider,
    val health: ProviderHealthStatus
)
