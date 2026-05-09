package com.pg.ochestration.application.service.command

import com.pg.ochestration.domain.model.Provider

data class ProviderConnectCommand(
    val merchantId: String,
    val provider: Provider,
    val displayName: String,
    val apiKey: String
)
