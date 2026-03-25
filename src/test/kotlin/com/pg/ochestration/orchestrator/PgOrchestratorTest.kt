package com.pg.ochestration.application.orchestration

import com.pg.ochestration.application.port.out.GatewayApproveCommand
import com.pg.ochestration.application.port.out.GatewayApproveResult
import com.pg.ochestration.application.port.out.GatewayCancelCommand
import com.pg.ochestration.application.port.out.GatewayCancelResult
import com.pg.ochestration.application.port.out.GatewayFailure
import com.pg.ochestration.application.port.out.GatewayPaymentQuery
import com.pg.ochestration.application.port.out.GatewayPaymentResult
import com.pg.ochestration.application.port.out.PaymentProviderGateway
import com.pg.ochestration.domain.service.ProviderCapabilityRegistry
import com.pg.ochestration.domain.model.AttemptResult
import com.pg.ochestration.domain.model.ConnectionStatus
import com.pg.ochestration.domain.model.FailureCategory
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderHealthStatus
import com.pg.ochestration.infrastructure.persistence.memory.PaymentRepository
import com.pg.ochestration.infrastructure.persistence.memory.ProviderConnectionRepository
import com.pg.ochestration.infrastructure.persistence.memory.ProviderHealthRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.time.Instant

class PgOrchestratorTest {
    @Test
    fun `retryable failure falls back to next provider`() {
        val orchestrator = newOrchestrator(
            gateways = listOf(
                FixedGateway(Provider.TOSS, technicalFailure()),
                FixedGateway(Provider.KAKAOPAY, success(Provider.KAKAOPAY, "kakao-tx-001")),
                FixedGateway(Provider.INICIS, success(Provider.INICIS, "inicis-tx-001"))
            )
        )

        val payment = runBlocking {
            orchestrator.approve(
                ApprovePaymentCommand(
                    merchantId = "merchant-001",
                    orderId = "order-001",
                    amount = 10_000,
                    currency = "KRW",
                    idempotencyKey = "idem-001",
                    requestedAt = Instant.now(),
                    preferredPrimaryProvider = null,
                    metadata = mapOf("paymentKey" to "test-payment-key")
                )
            )
        }

        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertEquals(Provider.KAKAOPAY, payment.approvedProvider)
        assertEquals(2, payment.attempts.size)
        assertEquals(AttemptResult.FAIL, payment.attempts[0].result)
        assertEquals(AttemptResult.SUCCESS, payment.attempts[1].result)
    }

    @Test
    fun `non retryable business failure stops fallback`() {
        val businessFailure = GatewayApproveResult(
            success = false,
            provider = Provider.TOSS,
            status = PaymentStatus.FAILED,
            failure = GatewayFailure(
                code = "CARD_LIMIT_EXCEEDED",
                category = FailureCategory.NON_RETRYABLE_BUSINESS,
                message = "Card limit exceeded"
            )
        )

        val orchestrator = newOrchestrator(
            gateways = listOf(
                FixedGateway(Provider.TOSS, businessFailure),
                FixedGateway(Provider.KAKAOPAY, success(Provider.KAKAOPAY, "kakao-tx-001")),
                FixedGateway(Provider.INICIS, success(Provider.INICIS, "inicis-tx-001"))
            )
        )

        val payment = runBlocking {
            orchestrator.approve(
                ApprovePaymentCommand(
                    merchantId = "merchant-001",
                    orderId = "order-001",
                    amount = 10_000,
                    currency = "KRW",
                    idempotencyKey = "idem-001",
                    requestedAt = Instant.now(),
                    preferredPrimaryProvider = null,
                    metadata = mapOf("paymentKey" to "test-payment-key")
                )
            )
        }

        assertEquals(PaymentStatus.FAILED, payment.status)
        assertEquals(1, payment.attempts.size)
        assertEquals(Provider.TOSS, payment.attempts.first().provider)
        assertNotNull(payment.selectionSummary.fallbackReason)
        assertEquals("CARD_LIMIT_EXCEEDED", payment.failureCode)
    }

    private fun newOrchestrator(gateways: List<PaymentProviderGateway>): PgOrchestrator {
        val connectionRepository = ProviderConnectionRepository().apply {
            upsert("merchant-001", Provider.TOSS, "Toss", ConnectionStatus.CONNECTED)
            upsert("merchant-001", Provider.KAKAOPAY, "Kakao", ConnectionStatus.CONNECTED)
            upsert("merchant-001", Provider.INICIS, "Inicis", ConnectionStatus.CONNECTED)
        }
        val healthRepository = ProviderHealthRepository().apply {
            set(Provider.TOSS, ProviderHealthStatus.HEALTHY)
            set(Provider.KAKAOPAY, ProviderHealthStatus.HEALTHY)
            set(Provider.INICIS, ProviderHealthStatus.HEALTHY)
        }
        val policy = ProviderSelectionPolicy(connectionRepository, healthRepository, ProviderCapabilityRegistry())
        return PgOrchestrator(policy, gateways, PaymentRepository())
    }

    private fun technicalFailure(): GatewayApproveResult {
        return GatewayApproveResult(
            success = false,
            provider = Provider.TOSS,
            status = PaymentStatus.FAILED,
            failure = GatewayFailure(
                code = "PG_TIMEOUT",
                category = FailureCategory.RETRYABLE_TECHNICAL,
                message = "Temporary timeout"
            )
        )
    }

    private fun success(provider: Provider, txId: String): GatewayApproveResult {
        return GatewayApproveResult(
            success = true,
            provider = provider,
            providerTxId = txId,
            approvedAt = Instant.now(),
            status = PaymentStatus.APPROVED
        )
    }

    private class FixedGateway(
        private val provider: Provider,
        private val approveResult: GatewayApproveResult
    ) : PaymentProviderGateway {
        override fun supports(provider: Provider): Boolean = this.provider == provider

        override suspend fun approve(command: GatewayApproveCommand): GatewayApproveResult = approveResult

        override suspend fun cancel(command: GatewayCancelCommand): GatewayCancelResult {
            return GatewayCancelResult(
                success = true,
                provider = provider,
                providerTxId = command.providerTxId,
                canceledAt = Instant.now(),
                status = PaymentStatus.CANCELED
            )
        }

        override suspend fun getPayment(query: GatewayPaymentQuery): GatewayPaymentResult {
            return GatewayPaymentResult(
                success = true,
                provider = provider,
                providerTxId = query.providerTxId,
                status = PaymentStatus.APPROVED,
                approvedAt = Instant.now()
            )
        }
    }
}
