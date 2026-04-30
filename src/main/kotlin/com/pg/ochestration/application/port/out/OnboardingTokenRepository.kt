package com.pg.ochestration.application.port.out

import com.pg.ochestration.domain.model.OnboardingToken

interface OnboardingTokenRepository {
    fun save(token: OnboardingToken): OnboardingToken
    fun consumeByRawToken(rawToken: String): OnboardingToken
}
