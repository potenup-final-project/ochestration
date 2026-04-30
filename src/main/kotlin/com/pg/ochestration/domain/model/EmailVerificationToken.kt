package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.InvalidEmailTokenException
import java.time.Instant

data class EmailVerificationToken(
    val tokenId: String,
    val merchantId: String,
    val tokenHash: String,
    val expiresAt: Instant,
    val used: Boolean = false
) {
    fun ensureValid(now: Instant = Instant.now()) {
        if (used) throw InvalidEmailTokenException("이미 사용된 이메일 인증 토큰입니다")
        if (expiresAt.isBefore(now)) throw InvalidEmailTokenException("만료된 이메일 인증 토큰입니다")
    }

    fun consume(): EmailVerificationToken = copy(used = true)
}
