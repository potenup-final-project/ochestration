package com.pg.ochestration.infrastructure.token

import com.pg.ochestration.application.port.out.EmailVerificationTokenRepository
import com.pg.ochestration.domain.model.EmailVerificationToken
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryEmailVerificationTokenAdapter : EmailVerificationTokenRepository {

    private val store: ConcurrentHashMap<String, EmailVerificationToken> = ConcurrentHashMap()

    override fun save(token: EmailVerificationToken): EmailVerificationToken {
        store[token.tokenHash] = token
        return token
    }

    override fun findByHash(tokenHash: String): EmailVerificationToken? =
        store[tokenHash]
}
