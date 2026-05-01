package com.pg.ochestration.infrastructure.auth

import com.pg.ochestration.application.port.out.OnboardingTokenRepository
import com.pg.ochestration.domain.exception.InvalidOnboardingTokenException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class OnboardingTokenInterceptor(
    private val onboardingTokenRepository: OnboardingTokenRepository
) : HandlerInterceptor {

    companion object {
        const val ATTR_KEY = "ONBOARDING_MERCHANT_ID"
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val rawToken = request.getHeader("X-Onboarding-Token")
            ?: throw InvalidOnboardingTokenException("X-Onboarding-Token 헤더가 필요합니다")

        val token = onboardingTokenRepository.consumeByRawToken(rawToken)
        request.setAttribute(ATTR_KEY, token.merchantId)
        return true
    }
}
