package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderHealthStatus
import com.pg.ochestration.infrastructure.persistence.jpa.entity.ProviderHealthJpaEntity
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ProviderHealthRepository(
    private val jpaRepository: ProviderHealthJpaRepository
) {
    @Transactional
    fun set(provider: Provider, health: ProviderHealthStatus): ProviderHealthStatus {
        val existing = jpaRepository.findById(provider).orElse(null)
        val entity = if (existing != null) {
            existing.updateHealth(health)
            existing
        } else {
            ProviderHealthJpaEntity(provider = provider, initialHealthStatus = health)
        }
        jpaRepository.saveAndFlush(entity)
        return health
    }

    fun get(provider: Provider): ProviderHealthStatus {
        return jpaRepository.findById(provider).map { it.healthStatus }.orElse(ProviderHealthStatus.HEALTHY)
    }

    fun findAll(): Map<Provider, ProviderHealthStatus> {
        val stored = jpaRepository.findAll().associateBy({ it.provider }, { it.healthStatus })
        return Provider.entries.associateWith { provider ->
            stored[provider] ?: ProviderHealthStatus.HEALTHY
        }
    }
}
