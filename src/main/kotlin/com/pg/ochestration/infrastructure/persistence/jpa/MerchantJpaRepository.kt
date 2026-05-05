package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.infrastructure.persistence.jpa.entity.MerchantJpaEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface MerchantJpaRepository : JpaRepository<MerchantJpaEntity, String> {
    fun findByEmail(email: String): Optional<MerchantJpaEntity>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MerchantJpaEntity m where m.merchantId = :merchantId")
    fun findByIdForUpdate(@Param("merchantId") merchantId: String): Optional<MerchantJpaEntity>
    fun existsByEmail(email: String): Boolean
}
