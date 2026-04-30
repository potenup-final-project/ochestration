package com.pg.ochestration.infrastructure.token

import com.pg.ochestration.application.port.out.OnboardingTokenRepository
import com.pg.ochestration.domain.exception.InvalidOnboardingTokenException
import com.pg.ochestration.domain.model.OnboardingToken
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryOnboardingTokenAdapter : OnboardingTokenRepository {

    private val store: ConcurrentHashMap<String, OnboardingToken> = ConcurrentHashMap()

    override fun save(token: OnboardingToken): OnboardingToken {
        store[token.tokenHash] = token
        return token
    }

    override fun consumeByRawToken(rawToken: String): OnboardingToken {
        val tokenHash = TokenHasher.sha256(rawToken)

        if (!store.containsKey(tokenHash)) {
            throw InvalidOnboardingTokenException("유효하지 않은 온보딩 토큰입니다")
        }

        var consumed: OnboardingToken? = null
        store.computeIfPresent(tokenHash) { _, token ->
            val now = Instant.now()
            if (!token.used && token.expiresAt.isAfter(now)) {
                consumed = token.consume()
                consumed
            } else {
                token
            }
        }

        return consumed
            ?: throw InvalidOnboardingTokenException("온보딩 토큰이 만료되었거나 이미 사용되었습니다")
    }
}
