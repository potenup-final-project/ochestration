package com.pg.ochestration.application.service

import com.pg.ochestration.application.service.command.ProviderConnectCommand
import com.pg.ochestration.domain.service.ProviderCapabilityRegistry
import com.pg.ochestration.domain.model.ConnectionStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderCapability
import com.pg.ochestration.domain.model.ProviderConnection
import com.pg.ochestration.domain.model.ProviderHealthStatus
import com.pg.ochestration.infrastructure.persistence.jpa.ProviderConnectionRepository
import com.pg.ochestration.infrastructure.persistence.jpa.ProviderHealthRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ProviderManagementService(
    private val connectionRepository: ProviderConnectionRepository,
    private val healthRepository: ProviderHealthRepository,
    private val capabilityRegistry: ProviderCapabilityRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun connect(command: ProviderConnectCommand): ProviderConnection {
        val result = connectionRepository.upsert(
            merchantId = command.merchantId,
            provider = command.provider,
            displayName = command.displayName,
            status = ConnectionStatus.CONNECTED
        )
        logger.info("[ProviderConnect] merchantId={} provider={} connected", command.merchantId, command.provider)
        return result
    }

    fun disconnect(merchantId: String, provider: Provider): ProviderConnection {
        val existing = connectionRepository.findByProvider(provider)
        val result = connectionRepository.upsert(
            merchantId = merchantId,
            provider = provider,
            displayName = existing?.displayName ?: provider.name,
            status = ConnectionStatus.DISCONNECTED
        )
        logger.info("[ProviderDisconnect] merchantId={} provider={} disconnected", merchantId, provider)
        return result
    }

    fun listConnections(): List<ProviderConnection> = connectionRepository.findAll()

    fun listCapabilities(): Map<Provider, ProviderCapability> = capabilityRegistry.listAll()

    fun updateHealth(provider: Provider, health: ProviderHealthStatus): ProviderHealthStatus {
        return healthRepository.set(provider, health)
    }

    fun listHealth(): Map<Provider, ProviderHealthStatus> = healthRepository.findAll()
}
