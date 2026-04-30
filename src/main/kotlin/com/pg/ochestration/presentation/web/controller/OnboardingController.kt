package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.application.usecase.GetMerchantStatusUseCase
import com.pg.ochestration.application.usecase.RegisterMerchantUseCase
import com.pg.ochestration.application.usecase.RequestLiveUpgradeUseCase
import com.pg.ochestration.application.usecase.VerifyEmailUseCase
import com.pg.ochestration.domain.model.MerchantStatus
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class RegisterRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
    val businessName: String? = null
)

data class RegisterResponse(
    val merchantId: String,
    val status: MerchantStatus,
    val message: String = "이메일 인증 링크를 발송했습니다"
)

data class VerifyEmailRequest(
    @field:NotBlank val token: String
)

data class VerifyEmailResponse(
    val merchantId: String,
    val status: MerchantStatus,
    val onboardingToken: String,
    val onboardingTokenExpiresAt: Instant
)

data class LiveUpgradeRequest(
    @field:NotBlank val businessRegistrationNumber: String,
    @field:NotBlank val businessRegistrationFileUrl: String
)

data class LiveUpgradeResponse(
    val merchantId: String,
    val status: MerchantStatus
)

data class MerchantStatusResponse(
    val merchantId: String,
    val email: String,
    val status: MerchantStatus,
    val businessName: String?
)

@RestController
@RequestMapping("/api/onboarding")
class OnboardingController(
    private val registerMerchantUseCase: RegisterMerchantUseCase,
    private val verifyEmailUseCase: VerifyEmailUseCase,
    private val requestLiveUpgradeUseCase: RequestLiveUpgradeUseCase,
    private val getMerchantStatusUseCase: GetMerchantStatusUseCase
) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): RegisterResponse {
        val result = registerMerchantUseCase.register(
            email = request.email,
            password = request.password,
            businessName = request.businessName
        )
        return RegisterResponse(merchantId = result.merchantId, status = result.status)
    }

    @PostMapping("/verify-email")
    fun verifyEmail(@Valid @RequestBody request: VerifyEmailRequest): VerifyEmailResponse {
        val result = verifyEmailUseCase.verify(request.token)
        return VerifyEmailResponse(
            merchantId = result.merchantId,
            status = result.status,
            onboardingToken = result.onboardingToken,
            onboardingTokenExpiresAt = result.expiresAt
        )
    }

    @PostMapping("/live-upgrade")
    fun requestLiveUpgrade(
        @Valid @RequestBody request: LiveUpgradeRequest,
        principal: MerchantPrincipal
    ): LiveUpgradeResponse {
        val result = requestLiveUpgradeUseCase.request(
            merchantId = principal.merchantId,
            businessRegistrationNumber = request.businessRegistrationNumber,
            businessRegistrationFileUrl = request.businessRegistrationFileUrl
        )
        return LiveUpgradeResponse(merchantId = result.merchantId, status = result.status)
    }

    @GetMapping("/me")
    fun getMyStatus(principal: MerchantPrincipal): MerchantStatusResponse {
        val result = getMerchantStatusUseCase.get(principal.merchantId)
        return MerchantStatusResponse(
            merchantId = result.merchantId,
            email = result.email,
            status = result.status,
            businessName = result.businessName
        )
    }
}
