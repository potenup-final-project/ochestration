package com.pg.ochestration.infrastructure.persistence.memory

import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderHealthStatus
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class ProviderHealthRepository {
    private val store = ConcurrentHashMap<Provider, ProviderHealthStatus>()

    fun set(provider: Provider, health: ProviderHealthStatus): ProviderHealthStatus {
        store[provider] = health
        return health
    }

    fun get(provider: Provider): ProviderHealthStatus = store[provider] ?: ProviderHealthStatus.HEALTHY

    fun findAll(): Map<Provider, ProviderHealthStatus> = Provider.entries.associateWith { get(it) }
}
