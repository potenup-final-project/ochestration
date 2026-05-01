package com.pg.ochestration.application.service

import com.pg.ochestration.application.orchestration.ApprovePaymentCommand
import com.pg.ochestration.application.orchestration.PgOrchestrator
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class ApprovePaymentHandler(
    private val pgOrchestrator: PgOrchestrator,
    private val sandboxPaymentSimulator: SandboxPaymentSimulator
) {
    suspend fun handle(
        principal: MerchantPrincipal,
        orderId: String,
        amount: Long,
        currency: String,
        idempotencyKey: String?,
        requestedAt: Instant?,
        preferredPrimaryProvider: Provider?,
        metadata: Map<String, String>
    ): Payment {
        val command = ApprovePaymentCommand(
            merchantId = principal.merchantId,
            orderId = orderId,
            amount = amount,
            currency = currency,
            idempotencyKey = idempotencyKey ?: UUID.randomUUID().toString(),
            requestedAt = requestedAt ?: Instant.now(),
            preferredPrimaryProvider = preferredPrimaryProvider,
            metadata = metadata
        )

        return if (principal.environment == ApiKeyEnvironment.SANDBOX) {
            sandboxPaymentSimulator.simulateApprove(command)
        } else {
            pgOrchestrator.approve(command)
        }
    }
}
