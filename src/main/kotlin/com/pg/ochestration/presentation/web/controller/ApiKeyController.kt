package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.application.usecase.IssueApiKeyUseCase
import com.pg.ochestration.application.usecase.RevokeApiKeyUseCase
import com.pg.ochestration.domain.exception.InvalidOnboardingTokenException
import com.pg.ochestration.domain.exception.MerchantNotEligibleForLiveException
import com.pg.ochestration.domain.exception.MissingApiKeyException
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.MerchantStatus
import com.pg.ochestration.infrastructure.auth.OnboardingTokenInterceptor
import com.pg.ochestration.presentation.web.controller.request.IssueApiKeyRequest
import com.pg.ochestration.presentation.web.controller.response.IssueApiKeyResponse
import com.pg.ochestration.presentation.web.controller.response.RevokeApiKeyResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("!prod")
@RestController
@RequestMapping("/api/auth/keys")
class ApiKeyController(
    private val issueApiKeyUseCase: IssueApiKeyUseCase,
    private val revokeApiKeyUseCase: RevokeApiKeyUseCase
) {
    @PostMapping
    fun issueKey(
        @RequestBody request: IssueApiKeyRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<IssueApiKeyResponse> {
        val merchantId = httpRequest.getAttribute(OnboardingTokenInterceptor.ATTR_KEY) as? String
            ?: throw InvalidOnboardingTokenException("온보딩 토큰 인증이 필요합니다")

        if (request.environment == ApiKeyEnvironment.LIVE) {
            throw MerchantNotEligibleForLiveException(
                merchantId = merchantId,
                status = MerchantStatus.SANDBOX_ACTIVE,
                reason = "LIVE Key는 관리자 승인 후 자동 발급됩니다. POST /api/onboarding/live-upgrade 를 통해 Live 전환을 신청하세요"
            )
        }

        val result = issueApiKeyUseCase.issue(request.toCommand(merchantId))
        return ResponseEntity.status(HttpStatus.CREATED).body(IssueApiKeyResponse.from(result))
    }

    @DeleteMapping("/{keyId}")
    fun revokeKey(
        @PathVariable keyId: String,
        httpRequest: HttpServletRequest
    ): RevokeApiKeyResponse {
        val rawKey = httpRequest.getHeader("X-Api-Key")
            ?: throw MissingApiKeyException()
        val revoked = revokeApiKeyUseCase.revoke(keyId = keyId, rawKey = rawKey)
        return RevokeApiKeyResponse.from(revoked)
    }
}
