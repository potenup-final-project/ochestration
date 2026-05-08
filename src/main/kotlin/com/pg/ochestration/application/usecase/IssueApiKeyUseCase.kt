package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.MerchantApiKeyRepository
import com.pg.ochestration.application.usecase.command.IssueApiKeyCommand
import com.pg.ochestration.application.usecase.result.IssueApiKeyResult
import com.pg.ochestration.domain.model.ApiKeyScope
import com.pg.ochestration.domain.model.ApiKeyStatus
import com.pg.ochestration.domain.model.MerchantApiKey
import com.pg.ochestration.infrastructure.auth.ApiKeyGenerator
import com.pg.ochestration.infrastructure.auth.ApiKeyHasher
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class IssueApiKeyUseCase(
    private val merchantApiKeyRepository: MerchantApiKeyRepository,
    private val apiKeyHasher: ApiKeyHasher,
    private val apiKeyGenerator: ApiKeyGenerator,
    @Value("\${auth.api-key.grace-period-hours:24}") private val gracePeriodHours: Long
) {
    @Transactional
    fun issue(command: IssueApiKeyCommand): IssueApiKeyResult {
        val now = Instant.now()
        val graceExpiredAt = now.plus(gracePeriodHours, ChronoUnit.HOURS)

        merchantApiKeyRepository.transitActiveToGracePeriod(
            merchantId = command.merchantId,
            environment = command.environment,
            graceExpiredAt = graceExpiredAt
        )

        val rawKey = apiKeyGenerator.generate(command.environment)
        val keyHash = apiKeyHasher.hash(rawKey)
        val keyPrefix = rawKey.take(12)

        val newApiKey = MerchantApiKey(
            keyId = UUID.randomUUID().toString(),
            merchantId = command.merchantId,
            keyHash = keyHash,
            keyPrefix = keyPrefix,
            environment = command.environment,
            status = ApiKeyStatus.ACTIVE,
            scopes = setOf(ApiKeyScope.PAYMENT_WRITE, ApiKeyScope.PAYMENT_READ),
            description = command.description,
            expiredAt = null,
            graceExpiredAt = null,
            revokedAt = null,
            createdAt = now,
            lastUsedAt = null
        )

        val saved = merchantApiKeyRepository.save(newApiKey)
        return IssueApiKeyResult(apiKey = saved, rawKey = rawKey)
    }
}
