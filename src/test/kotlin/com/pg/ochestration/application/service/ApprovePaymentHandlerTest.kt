package com.pg.ochestration.application.service

import com.pg.ochestration.application.orchestration.ApprovePaymentCommand
import com.pg.ochestration.application.orchestration.PgOrchestrator
import com.pg.ochestration.application.port.out.PaymentIdGeneratorPort
import com.pg.ochestration.application.port.out.PaymentProviderGateway
import com.pg.ochestration.application.port.out.PaymentSavePort
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.SelectionSummary
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import com.pg.ochestration.infrastructure.persistence.jpa.PaymentRepository
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApprovePaymentHandlerTest {

    @Test
    fun `LIVE 환경 principal은 PgOrchestrator approve를 호출한다`() {
        val trackingOrchestrator = TrackingPgOrchestrator()
        val handler = buildHandler(pgOrchestrator = trackingOrchestrator)
        val principal = livePrincipal()

        val result = runBlocking {
            handler.handle(
                principal = principal,
                orderId = "order-001",
                amount = 10_000L,
                currency = "KRW",
                idempotencyKey = null,
                requestedAt = null,
                preferredPrimaryProvider = null,
                metadata = emptyMap()
            )
        }

        assertNotNull(result)
        assertEquals(PaymentStatus.APPROVED, result.status)
        assertEquals(1, trackingOrchestrator.approveCallCount)
    }

    @Test
    fun `SANDBOX 환경 principal은 SandboxPaymentSimulator를 호출하고 PgOrchestrator를 호출하지 않는다`() {
        val trackingOrchestrator = TrackingPgOrchestrator()
        val handler = buildHandler(pgOrchestrator = trackingOrchestrator)
        val principal = sandboxPrincipal()

        val result = runBlocking {
            handler.handle(
                principal = principal,
                orderId = "order-sandbox-001",
                amount = 5_000L,
                currency = "KRW",
                idempotencyKey = null,
                requestedAt = null,
                preferredPrimaryProvider = null,
                metadata = emptyMap()
            )
        }

        assertEquals(PaymentStatus.APPROVED, result.status)
        assertEquals(0, trackingOrchestrator.approveCallCount)
    }

    @Test
    fun `SANDBOX 환경에서 orderId에 fail이 포함되면 FAILED 상태를 반환한다`() {
        val trackingOrchestrator = TrackingPgOrchestrator()
        val handler = buildHandler(pgOrchestrator = trackingOrchestrator)
        val principal = sandboxPrincipal()

        val result = runBlocking {
            handler.handle(
                principal = principal,
                orderId = "order-fail-sandbox",
                amount = 5_000L,
                currency = "KRW",
                idempotencyKey = null,
                requestedAt = null,
                preferredPrimaryProvider = null,
                metadata = emptyMap()
            )
        }

        assertEquals(PaymentStatus.FAILED, result.status)
        assertEquals(0, trackingOrchestrator.approveCallCount)
    }
}

// -------------------------------------------------------------------------
// Fixtures & Stubs
// -------------------------------------------------------------------------

private fun livePrincipal() = MerchantPrincipal(
    merchantId = "merchant-live-001",
    environment = ApiKeyEnvironment.LIVE
)

private fun sandboxPrincipal() = MerchantPrincipal(
    merchantId = "merchant-sandbox-001",
    environment = ApiKeyEnvironment.SANDBOX
)

@Suppress("UNCHECKED_CAST")
private fun <T> nullStub(): T = null as T

private fun buildHandler(pgOrchestrator: PgOrchestrator): ApprovePaymentHandler {
    val fakePaymentSavePort = FakePaymentSavePortForApproveTest()
    val sandboxSimulator = SandboxPaymentSimulator(fakePaymentSavePort)
    return ApprovePaymentHandler(pgOrchestrator, sandboxSimulator)
}

private class TrackingPgOrchestrator : PgOrchestrator(
    paymentIdGenerator = FixedIdGenerator(),
    providerSelectionPolicy = nullStub(),
    gateways = emptyList<PaymentProviderGateway>(),
    paymentRepository = nullStub()
) {
    var approveCallCount = 0

    override suspend fun approve(command: ApprovePaymentCommand): Payment {
        approveCallCount++
        return Payment(
            paymentId = "pay-live-${UUID.randomUUID()}",
            merchantId = command.merchantId,
            orderId = command.orderId,
            amount = command.amount,
            currency = command.currency,
            idempotencyKey = command.idempotencyKey,
            requestedAt = command.requestedAt,
            status = PaymentStatus.APPROVED,
            approvedProvider = Provider.TOSS,
            providerTxId = "toss-tx-001",
            approvedAt = Instant.now(),
            attempts = emptyList(),
            selectionSummary = SelectionSummary.sandbox(),
            metadata = command.metadata
        )
    }
}

private class FixedIdGenerator : PaymentIdGeneratorPort {
    override fun generate(): String = UUID.randomUUID().toString()
}

private class FakePaymentSavePortForApproveTest : PaymentSavePort {
    private val store: MutableMap<String, Payment> = mutableMapOf()

    override fun save(payment: Payment): Payment {
        store[payment.paymentId] = payment
        return payment
    }

    override fun findById(paymentId: String): Payment? = store[paymentId]
}
