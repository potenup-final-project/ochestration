package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.application.usecase.GetMerchantStatusUseCase
import com.pg.ochestration.application.usecase.RegisterMerchantUseCase
import com.pg.ochestration.application.usecase.RequestLiveUpgradeUseCase
import com.pg.ochestration.application.usecase.VerifyEmailUseCase
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import com.pg.ochestration.presentation.web.controller.request.LiveUpgradeRequest
import com.pg.ochestration.presentation.web.controller.request.RegisterRequest
import com.pg.ochestration.presentation.web.controller.request.VerifyEmailRequest
import com.pg.ochestration.presentation.web.controller.response.LiveUpgradeResponse
import com.pg.ochestration.presentation.web.controller.response.MerchantStatusResponse
import com.pg.ochestration.presentation.web.controller.response.RegisterResponse
import com.pg.ochestration.presentation.web.controller.response.VerifyEmailResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

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
