package com.pg.ochestration.infrastructure.gateway.pg

import com.pg.ochestration.application.port.out.GatewayApproveCommand
import com.pg.ochestration.application.port.out.GatewayApproveResult
import com.pg.ochestration.application.port.out.GatewayCancelCommand
import com.pg.ochestration.application.port.out.GatewayCancelResult
import com.pg.ochestration.application.port.out.GatewayFailure
import com.pg.ochestration.application.port.out.GatewayPaymentQuery
import com.pg.ochestration.application.port.out.GatewayPaymentResult
import com.pg.ochestration.application.port.out.PaymentProviderGateway
import com.pg.ochestration.domain.service.FailureClassifier
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@Component
class InicisPgAdapter(
    private val failureClassifier: FailureClassifier
) : PaymentProviderGateway {
    private val txSequence = AtomicInteger(1)

    override fun supports(provider: Provider): Boolean = provider == Provider.INICIS

    override suspend fun approve(command: GatewayApproveCommand): GatewayApproveResult {
        if (command.metadata["stubFail"] == "true") {
            return failure(Provider.INICIS, "PG_TIMEOUT", "Inicis stub transient failure")
        }

        return GatewayApproveResult(
            success = true,
            provider = Provider.INICIS,
            providerTxId = nextTxId(),
            approvedAt = Instant.now(),
            status = PaymentStatus.APPROVED,
            metadata = mapOf("mode" to "stub")
        )
    }

    override suspend fun cancel(command: GatewayCancelCommand): GatewayCancelResult {
        return GatewayCancelResult(
            success = true,
            provider = Provider.INICIS,
            providerTxId = command.providerTxId,
            canceledAt = Instant.now(),
            status = PaymentStatus.CANCELED,
            metadata = mapOf("mode" to "stub")
        )
    }

    override suspend fun getPayment(query: GatewayPaymentQuery): GatewayPaymentResult {
        return GatewayPaymentResult(
            success = true,
            provider = Provider.INICIS,
            providerTxId = query.providerTxId,
            status = PaymentStatus.APPROVED,
            approvedAt = Instant.now(),
            metadata = mapOf("mode" to "stub")
        )
    }

    private fun nextTxId(): String = "inicis-stub-${txSequence.getAndIncrement().toString().padStart(4, '0')}"

    private fun failure(provider: Provider, code: String, message: String): GatewayApproveResult {
        return GatewayApproveResult(
            success = false,
            provider = provider,
            status = PaymentStatus.FAILED,
            failure = GatewayFailure(
                code = code,
                category = failureClassifier.classify(code),
                message = message
            )
        )
    }
}
