package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.infrastructure.persistence.jpa.entity.MerchantJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface MerchantJpaRepository : JpaRepository<MerchantJpaEntity, String> {
    fun findByEmail(email: String): Optional<MerchantJpaEntity>
    fun existsByEmail(email: String): Boolean
}
