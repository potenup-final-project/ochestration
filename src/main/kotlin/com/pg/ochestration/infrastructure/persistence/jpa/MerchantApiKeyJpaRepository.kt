package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.ApiKeyStatus
import com.pg.ochestration.infrastructure.persistence.jpa.entity.MerchantApiKeyJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface MerchantApiKeyJpaRepository : JpaRepository<MerchantApiKeyJpaEntity, String> {

    fun findByKeyHashAndStatusIn(
        keyHash: String,
        statuses: List<ApiKeyStatus>
    ): MerchantApiKeyJpaEntity?

    @Modifying
    @Query("""
        UPDATE MerchantApiKeyJpaEntity e
        SET e.status = com.pg.ochestration.domain.model.ApiKeyStatus.GRACE_PERIOD,
            e.graceExpiredAt = :graceExpiredAt
        WHERE e.merchantId = :merchantId
          AND e.environment = :environment
          AND e.status = com.pg.ochestration.domain.model.ApiKeyStatus.ACTIVE
    """)
    fun transitActiveToGracePeriod(
        merchantId: String,
        environment: ApiKeyEnvironment,
        graceExpiredAt: Instant
    ): Int

    fun findAllByMerchantId(merchantId: String): List<MerchantApiKeyJpaEntity>
}
