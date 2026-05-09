package com.pg.ochestration.application.orchestration.command

import com.pg.ochestration.domain.model.Provider
import java.time.Instant

data class ApprovePaymentCommand(
    val merchantId: String,
    val orderId: String,
    val amount: Long,
    val currency: String,
    val idempotencyKey: String,
    val requestedAt: Instant,
    val preferredPrimaryProvider: Provider?,
    val metadata: Map<String, String>
)
