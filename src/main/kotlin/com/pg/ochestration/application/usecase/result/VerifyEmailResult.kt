package com.pg.ochestration.application.usecase.result

import com.pg.ochestration.domain.model.MerchantStatus
import java.time.Instant

data class VerifyEmailResult(
    val merchantId: String,
    val status: MerchantStatus,
    val onboardingToken: String,
    val expiresAt: Instant
)
