package com.pg.ochestration.domain.service

import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderCapability
import org.springframework.stereotype.Component

@Component
class ProviderCapabilityRegistry {
    private val capabilities: Map<Provider, ProviderCapability> = mapOf(
        Provider.TOSS to ProviderCapability(
            approve = true,
            cancel = true,
            getPayment = true,
            billing = true,
            settlement = true
        ),
        Provider.KAKAOPAY to ProviderCapability(
            approve = true,
            cancel = true,
            getPayment = true,
            billing = true,
            settlement = false
        ),
        Provider.INICIS to ProviderCapability(
            approve = true,
            cancel = true,
            getPayment = true,
            billing = true,
            settlement = true
        )
    )

    fun get(provider: Provider): ProviderCapability =
        capabilities[provider] ?: error("Capability is missing for provider=$provider")

    fun listAll(): Map<Provider, ProviderCapability> = capabilities
}
