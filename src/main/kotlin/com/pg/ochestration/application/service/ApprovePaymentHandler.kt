package com.pg.ochestration.application.service

import com.pg.ochestration.application.orchestration.PgOrchestrator
import com.pg.ochestration.application.orchestration.command.ApprovePaymentCommand
import com.pg.ochestration.application.service.command.UnifiedPaymentApproveCommand
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.Payment
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class ApprovePaymentHandler(
    private val pgOrchestrator: PgOrchestrator,
    private val sandboxPaymentSimulator: SandboxPaymentSimulator
) {
    suspend fun handle(command: UnifiedPaymentApproveCommand): Payment {
        val approveCommand = ApprovePaymentCommand(
            merchantId = command.merchantId,
            orderId = command.orderId,
            amount = command.amount,
            currency = command.currency,
            idempotencyKey = command.idempotencyKey ?: UUID.randomUUID().toString(),
            requestedAt = command.requestedAt ?: Instant.now(),
            preferredPrimaryProvider = command.preferredPrimaryProvider,
            metadata = command.metadata
        )

        return if (command.environment == ApiKeyEnvironment.SANDBOX) {
            sandboxPaymentSimulator.simulateApprove(approveCommand)
        } else {
            pgOrchestrator.approve(approveCommand)
        }
    }
}
