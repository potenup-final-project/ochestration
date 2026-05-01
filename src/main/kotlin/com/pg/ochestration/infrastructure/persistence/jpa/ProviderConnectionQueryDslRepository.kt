package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.domain.model.ConnectionStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.infrastructure.persistence.jpa.entity.ProviderConnectionJpaEntity

interface ProviderConnectionQueryDslRepository {
    fun findByProvider(provider: Provider): ProviderConnectionJpaEntity?
    fun getConnectionStatus(provider: Provider): ConnectionStatus
}
