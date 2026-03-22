package com.pg.ochestration.infrastructure.persistence.memory

import com.pg.ochestration.domain.model.ConnectionStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderConnection
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Repository
class ProviderConnectionRepository {
    private val store = ConcurrentHashMap<Provider, ProviderConnection>()
    private val sequence = AtomicInteger(1)

    fun upsert(
        merchantId: String,
        provider: Provider,
        displayName: String,
        status: ConnectionStatus
    ): ProviderConnection {
        val existing = store[provider]
        val connection = ProviderConnection(
            providerConnectionId = existing?.providerConnectionId ?: nextConnectionId(),
            merchantId = merchantId,
            provider = provider,
            displayName = displayName,
            status = status
        )
        store[provider] = connection
        return connection
    }

    fun findAll(): List<ProviderConnection> =
        Provider.entries.map { provider ->
            store[provider] ?: ProviderConnection(
                providerConnectionId = nextConnectionId(),
                merchantId = "merchant-001",
                provider = provider,
                displayName = provider.name,
                status = ConnectionStatus.DISCONNECTED
            ).also { store[provider] = it }
        }

    fun findByProvider(provider: Provider): ProviderConnection? = store[provider]

    fun getConnectionStatus(provider: Provider): ConnectionStatus =
        store[provider]?.status ?: ConnectionStatus.DISCONNECTED

    private fun nextConnectionId(): String = "pc-${sequence.getAndIncrement().toString().padStart(3, '0')}"
}
