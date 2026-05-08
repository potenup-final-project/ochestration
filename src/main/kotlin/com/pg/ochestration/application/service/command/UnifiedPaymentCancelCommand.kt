package com.pg.ochestration.application.service.command

import com.pg.ochestration.domain.model.ApiKeyEnvironment
import java.time.Instant

data class UnifiedPaymentCancelCommand(
    val merchantId: String,
    val environment: ApiKeyEnvironment,
    val paymentId: String,
    val reason: String,
    val idempotencyKey: String?,
    val requestedAt: Instant?
)
