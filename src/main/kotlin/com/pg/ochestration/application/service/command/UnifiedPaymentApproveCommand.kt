package com.pg.ochestration.application.service.command

import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.Provider
import java.time.Instant

data class UnifiedPaymentApproveCommand(
    val merchantId: String,
    val environment: ApiKeyEnvironment,
    val orderId: String,
    val amount: Long,
    val currency: String,
    val idempotencyKey: String?,
    val requestedAt: Instant?,
    val preferredPrimaryProvider: Provider?,
    val metadata: Map<String, String>
)
