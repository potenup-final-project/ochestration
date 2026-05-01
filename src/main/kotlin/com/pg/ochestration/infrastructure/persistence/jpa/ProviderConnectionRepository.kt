package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.domain.model.ConnectionStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderConnection
import com.pg.ochestration.infrastructure.persistence.jpa.entity.ProviderConnectionJpaEntity
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class ProviderConnectionRepository(
    private val jpaRepository: ProviderConnectionJpaRepository,
    private val queryDslRepository: ProviderConnectionQueryDslRepository
) {
    @Transactional
    fun upsert(
        merchantId: String,
        provider: Provider,
        displayName: String,
        status: ConnectionStatus
    ): ProviderConnection {
        val existing = jpaRepository.findByProvider(provider)
        val entity = if (existing != null) {
            existing.update(merchantId = merchantId, displayName = displayName, status = status)
            existing
        } else {
            ProviderConnectionJpaEntity(
                providerConnectionId = UUID.randomUUID().toString(),
                provider = provider,
                initialMerchantId = merchantId,
                initialDisplayName = displayName,
                initialStatus = status
            )
        }
        return toDomain(jpaRepository.save(entity))
    }

    @Transactional
    fun findAll(): List<ProviderConnection> {
        val existing = jpaRepository.findAll().associateBy { it.provider }
        return Provider.entries.map { provider ->
            existing[provider]?.let { toDomain(it) }
                ?: toDomain(ensureExists(provider))
        }
    }

    fun findByProvider(provider: Provider): ProviderConnection? {
        return queryDslRepository.findByProvider(provider)?.let { toDomain(it) }
    }

    fun getConnectionStatus(provider: Provider): ConnectionStatus {
        return queryDslRepository.getConnectionStatus(provider)
    }

    private fun ensureExists(provider: Provider): ProviderConnectionJpaEntity {
        val created = ProviderConnectionJpaEntity(
            providerConnectionId = UUID.randomUUID().toString(),
            provider = provider,
            initialMerchantId = "system",
            initialDisplayName = provider.name,
            initialStatus = ConnectionStatus.DISCONNECTED
        )
        return jpaRepository.save(created)
    }

    private fun toDomain(entity: ProviderConnectionJpaEntity): ProviderConnection {
        return ProviderConnection(
            providerConnectionId = entity.providerConnectionId,
            merchantId = entity.merchantId,
            provider = entity.provider,
            displayName = entity.displayName,
            status = entity.status
        )
    }
}
