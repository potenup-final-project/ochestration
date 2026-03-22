package com.pg.ochestration.domain.model

data class ProviderConnection(
    val providerConnectionId: String,
    val merchantId: String,
    val provider: Provider,
    val displayName: String,
    val status: ConnectionStatus
)
