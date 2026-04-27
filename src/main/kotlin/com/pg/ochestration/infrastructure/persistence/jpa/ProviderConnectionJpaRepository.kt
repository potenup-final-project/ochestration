package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.infrastructure.persistence.jpa.entity.ProviderConnectionJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ProviderConnectionJpaRepository : JpaRepository<ProviderConnectionJpaEntity, String> {
    fun findByProvider(provider: Provider): ProviderConnectionJpaEntity?
}
