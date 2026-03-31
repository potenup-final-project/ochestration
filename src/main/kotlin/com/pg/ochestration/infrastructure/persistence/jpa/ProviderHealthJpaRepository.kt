package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.infrastructure.persistence.jpa.entity.ProviderHealthJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ProviderHealthJpaRepository : JpaRepository<ProviderHealthJpaEntity, Provider>
