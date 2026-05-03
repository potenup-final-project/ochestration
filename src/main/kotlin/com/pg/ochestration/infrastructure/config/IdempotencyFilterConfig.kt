package com.pg.ochestration.infrastructure.config

import com.pg.ochestration.application.port.out.MerchantApiKeyRepository
import com.pg.ochestration.infrastructure.auth.ApiKeyCache
import com.pg.ochestration.infrastructure.auth.ApiKeyHasher
import com.pg.ochestration.infrastructure.idempotency.IdempotencyFilter
import com.pg.ochestration.infrastructure.idempotency.IdempotencyRedisStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class IdempotencyFilterConfig {

    @Bean
    fun idempotencyFilterRegistration(
        idempotencyRedisStore: IdempotencyRedisStore,
        apiKeyHasher: ApiKeyHasher,
        merchantApiKeyRepository: MerchantApiKeyRepository,
        apiKeyCache: ApiKeyCache,
        objectMapper: ObjectMapper,
        @Value("\${auth.api-key.enabled:true}") authEnabled: Boolean
    ): FilterRegistrationBean<IdempotencyFilter> =
        FilterRegistrationBean(
            IdempotencyFilter(
                idempotencyRedisStore = idempotencyRedisStore,
                apiKeyHasher = apiKeyHasher,
                merchantApiKeyRepository = merchantApiKeyRepository,
                apiKeyCache = apiKeyCache,
                objectMapper = objectMapper,
                authEnabled = authEnabled
            )
        ).apply {
            addUrlPatterns(
                "/api/payments/approve",
                "/api/payments/*/cancel"
            )
            setOrder(10)
            setName("idempotencyFilter")
        }
}
