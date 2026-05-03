package com.pg.ochestration.infrastructure.config

import com.pg.ochestration.infrastructure.auth.ApiKeyHasher
import com.pg.ochestration.infrastructure.idempotency.IdempotencyFilter
import com.pg.ochestration.infrastructure.idempotency.IdempotencyRedisStore
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
        objectMapper: ObjectMapper
    ): FilterRegistrationBean<IdempotencyFilter> =
        FilterRegistrationBean(IdempotencyFilter(idempotencyRedisStore, apiKeyHasher, objectMapper)).apply {
            addUrlPatterns(
                "/api/payments/approve",
                "/api/payments/*/cancel"
            )
            setOrder(10)
            setName("idempotencyFilter")
        }
}
