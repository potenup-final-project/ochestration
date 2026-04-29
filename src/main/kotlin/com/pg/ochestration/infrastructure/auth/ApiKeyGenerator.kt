package com.pg.ochestration.infrastructure.auth

import com.pg.ochestration.domain.model.ApiKeyEnvironment
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

@Component
class ApiKeyGenerator {

    private val secureRandom = SecureRandom()

    fun generate(environment: ApiKeyEnvironment): String {
        val prefix = when (environment) {
            ApiKeyEnvironment.SANDBOX -> "sk_test_"
            ApiKeyEnvironment.LIVE -> "sk_live_"
        }
        val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return "$prefix$suffix"
    }
}
