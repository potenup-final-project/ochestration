package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.OnboardingTokenExpiredException
import java.time.Instant

data class OnboardingToken(
    val tokenId: String,
    val merchantId: String,
    val tokenHash: String,
    val expiresAt: Instant,
    val used: Boolean = false
) {
    fun ensureValid(now: Instant = Instant.now()) {
        if (used) throw OnboardingTokenExpiredException("이미 사용된 온보딩 토큰입니다")
        if (expiresAt.isBefore(now)) throw OnboardingTokenExpiredException("만료된 온보딩 토큰입니다")
    }

    fun consume(): OnboardingToken = copy(used = true)
}
