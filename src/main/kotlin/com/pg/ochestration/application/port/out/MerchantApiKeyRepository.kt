package com.pg.ochestration.application.port.out

import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.MerchantApiKey
import java.time.Instant

interface MerchantApiKeyRepository {
    fun findActiveByHash(keyHash: String): MerchantApiKey?
    fun save(apiKey: MerchantApiKey): MerchantApiKey
    fun findAllByMerchantId(merchantId: String): List<MerchantApiKey>
    fun findById(keyId: String): MerchantApiKey?
    fun transitActiveToGracePeriod(
        merchantId: String,
        environment: ApiKeyEnvironment,
        graceExpiredAt: Instant
    ): Int
}
