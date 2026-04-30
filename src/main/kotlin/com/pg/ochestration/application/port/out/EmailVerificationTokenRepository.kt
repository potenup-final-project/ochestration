package com.pg.ochestration.application.port.out

import com.pg.ochestration.domain.model.EmailVerificationToken

interface EmailVerificationTokenRepository {
    fun save(token: EmailVerificationToken): EmailVerificationToken
    fun findByHash(tokenHash: String): EmailVerificationToken?
}
