package com.pg.ochestration.application.orchestration

import com.pg.ochestration.application.port.out.GatewayApproveCommand
import com.pg.ochestration.application.port.out.GatewayApproveResult
import com.pg.ochestration.application.port.out.GatewayCancelCommand
import com.pg.ochestration.application.port.out.GatewayCancelResult
import com.pg.ochestration.application.port.out.GatewayFailure
import com.pg.ochestration.application.port.out.GatewayPaymentQuery
import com.pg.ochestration.application.port.out.GatewayPaymentResult
import com.pg.ochestration.application.port.out.PaymentIdGeneratorPort
import com.pg.ochestration.application.port.out.PaymentProviderGateway
import com.pg.ochestration.domain.model.AttemptResult
import com.pg.ochestration.domain.model.FailureCategory
import com.pg.ochestration.domain.model.FallbackReasonCode
import com.pg.ochestration.domain.model.FilteredOutProvider
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderSelectionResult
import com.pg.ochestration.domain.model.SelectionPrimaryReason
import com.pg.ochestration.infrastructure.persistence.jpa.PaymentRepository
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PgOrchestratorTest {

    @Test
    fun `retryable failure falls back to next provider`() {
        val orchestrator = newOrchestrator(
            candidates = listOf(Provider.TOSS, Provider.KAKAOPAY, Provider.INICIS),
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
            candidates = listOf(Provider.TOSS, Provider.KAKAOPAY, Provider.INICIS),
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
        assertNotNull(payment.selectionSummary.fallbackReasonCode)
        assertEquals(FallbackReasonCode.NON_RETRYABLE_STOP, payment.selectionSummary.fallbackReasonCode)
        assertEquals("CARD_LIMIT_EXCEEDED", payment.failureCode)
    }

    private fun newOrchestrator(
        candidates: List<Provider>,
        gateways: List<PaymentProviderGateway>
    ): PgOrchestrator {
        val fakePolicy = FakeProviderSelectionPolicy(candidates)
        val fakeRepository = InMemoryPaymentRepository()
        val fakeIdGenerator = FixedPaymentIdGenerator()
        return PgOrchestrator(fakeIdGenerator, fakePolicy, gateways, fakeRepository)
    }

    private fun technicalFailure(): GatewayApproveResult =
        GatewayApproveResult(
            success = false,
            provider = Provider.TOSS,
            status = PaymentStatus.FAILED,
            failure = GatewayFailure(
                code = "PG_TIMEOUT",
                category = FailureCategory.RETRYABLE_TECHNICAL,
                message = "Temporary timeout"
            )
        )

    private fun success(provider: Provider, txId: String): GatewayApproveResult =
        GatewayApproveResult(
            success = true,
            provider = provider,
            providerTxId = txId,
            approvedAt = Instant.now(),
            status = PaymentStatus.APPROVED
        )

    private class FixedGateway(
        private val provider: Provider,
        private val approveResult: GatewayApproveResult
    ) : PaymentProviderGateway {
        override fun supports(provider: Provider): Boolean = this.provider == provider

        override suspend fun approve(command: GatewayApproveCommand): GatewayApproveResult = approveResult

        override suspend fun cancel(command: GatewayCancelCommand): GatewayCancelResult =
            GatewayCancelResult(
                success = true,
                provider = provider,
                providerTxId = command.providerTxId,
                canceledAt = Instant.now(),
                status = PaymentStatus.CANCELED
            )

        override suspend fun getPayment(query: GatewayPaymentQuery): GatewayPaymentResult =
            GatewayPaymentResult(
                success = true,
                provider = provider,
                providerTxId = query.providerTxId,
                status = PaymentStatus.APPROVED,
                approvedAt = Instant.now()
            )
    }
}

// -------------------------------------------------------------------------
// 테스트 스텁 — Spring 컨텍스트 불필요.
// allOpen 플러그인이 @Component/@Repository/@Service 클래스를 서브클래싱 가능하게 열어줌.
// -------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
private fun <T> nullStub(): T = null as T

private class FixedPaymentIdGenerator : PaymentIdGeneratorPort {
    override fun generate(): String = UUID.randomUUID().toString()
}

private class FakeProviderSelectionPolicy(
    private val candidates: List<Provider>
) : ProviderSelectionPolicy(
    connectionRepository = nullStub(),
    healthRepository = nullStub(),
    capabilityRegistry = nullStub()
) {
    override fun selectForApprove(merchantId: String, preferredPrimaryProvider: Provider?): ProviderSelectionResult =
        ProviderSelectionResult(
            initialCandidates = candidates,
            filteredOutProviders = emptyList<FilteredOutProvider>(),
            candidates = candidates,
            selectedPrimaryProvider = candidates.firstOrNull(),
            selectedPrimaryReason = SelectionPrimaryReason.HIGHEST_PRIORITY_DEFAULT
        )
}

private class InMemoryPaymentRepository : PaymentRepository(
    jpaRepository = nullStub(),
    selectionSummaryJpaRepository = nullStub(),
    queryDslRepository = nullStub(),
    objectMapper = nullStub()
) {
    private val store = ConcurrentHashMap<String, Payment>()

    override fun save(payment: Payment): Payment {
        store[payment.paymentId] = payment
        return payment
    }

    override fun findById(paymentId: String): Payment? = store[paymentId]
}
