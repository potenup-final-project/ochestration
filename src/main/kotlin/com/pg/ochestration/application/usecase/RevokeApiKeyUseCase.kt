package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.ApiKeyCachePort
import com.pg.ochestration.application.port.out.MerchantApiKeyRepository
import com.pg.ochestration.domain.exception.InvalidApiKeyException
import com.pg.ochestration.domain.model.MerchantApiKey
import com.pg.ochestration.infrastructure.auth.ApiKeyHasher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class RevokeApiKeyUseCase(
    private val merchantApiKeyRepository: MerchantApiKeyRepository,
    private val apiKeyHasher: ApiKeyHasher,
    private val apiKeyCachePort: ApiKeyCachePort
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun revoke(keyId: String, rawKey: String): MerchantApiKey {
        val keyHash = apiKeyHasher.hash(rawKey)
        val apiKey = merchantApiKeyRepository.findActiveByHash(keyHash)
            ?: throw InvalidApiKeyException()

        if (apiKey.keyId != keyId) throw InvalidApiKeyException()

        val revoked = apiKey.revoke(Instant.now())
        val saved = merchantApiKeyRepository.save(revoked)

        try {
            apiKeyCachePort.evict(keyHash)
        } catch (e: Exception) {
            log.warn("캐시 evict 실패: keyId={} — TTL 만료 후 자동 무효화됩니다", keyId, e)
        }

        log.info("API Key 폐기 완료: keyId={}, merchantId={}", keyId, apiKey.merchantId)
        return saved
    }
}
