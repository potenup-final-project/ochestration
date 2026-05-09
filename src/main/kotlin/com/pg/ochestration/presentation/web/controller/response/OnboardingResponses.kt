package com.pg.ochestration.presentation.web.controller.response

import com.pg.ochestration.domain.model.MerchantStatus
import java.time.Instant

data class RegisterResponse(
    val merchantId: String,
    val status: MerchantStatus,
    val message: String = "이메일 인증 링크를 발송했습니다"
)

data class VerifyEmailResponse(
    val merchantId: String,
    val status: MerchantStatus,
    val onboardingToken: String,
    val onboardingTokenExpiresAt: Instant
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
