package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.application.port.out.MerchantApiKeyRepository
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.ApiKeyStatus
import com.pg.ochestration.domain.model.MerchantApiKey
import com.pg.ochestration.infrastructure.persistence.jpa.entity.MerchantApiKeyJpaEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional(readOnly = true)
class MerchantApiKeyAdapter(
    private val jpaRepository: MerchantApiKeyJpaRepository
) : MerchantApiKeyRepository {

    override fun findActiveByHash(keyHash: String): MerchantApiKey? =
        jpaRepository.findByKeyHashAndStatusIn(
            keyHash,
            listOf(ApiKeyStatus.ACTIVE, ApiKeyStatus.GRACE_PERIOD)
        )?.toDomain()

    @Transactional
    override fun save(apiKey: MerchantApiKey): MerchantApiKey =
        jpaRepository.save(MerchantApiKeyJpaEntity.from(apiKey)).toDomain()

    override fun findAllByMerchantId(merchantId: String): List<MerchantApiKey> =
        jpaRepository.findAllByMerchantId(merchantId).map { it.toDomain() }

    override fun findById(keyId: String): MerchantApiKey? =
        jpaRepository.findById(keyId).orElse(null)?.toDomain()

    @Transactional
    override fun transitActiveToGracePeriod(
        merchantId: String,
        environment: ApiKeyEnvironment,
        graceExpiredAt: Instant
    ): Int = jpaRepository.transitActiveToGracePeriod(
        merchantId = merchantId,
        environment = environment,
        graceExpiredAt = graceExpiredAt
    )
}
