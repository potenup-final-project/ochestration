package com.pg.ochestration.application.service

import com.pg.ochestration.application.port.out.GatewayCancelCommand
import com.pg.ochestration.application.port.out.GatewayCancelResult
import com.pg.ochestration.application.port.out.GatewayApproveCommand
import com.pg.ochestration.application.port.out.GatewayApproveResult
import com.pg.ochestration.application.port.out.GatewayPaymentQuery
import com.pg.ochestration.application.port.out.GatewayPaymentResult
import com.pg.ochestration.application.port.out.PaymentProviderGateway
import com.pg.ochestration.application.port.out.PaymentSavePort
import com.pg.ochestration.application.service.command.UnifiedPaymentCancelCommand
import com.pg.ochestration.domain.exception.PaymentNotFoundException
import com.pg.ochestration.domain.exception.PaymentNotCancelableException
import com.pg.ochestration.domain.exception.PaymentAccessDeniedException
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.FailureCategory
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.SelectionSummary
import com.pg.ochestration.infrastructure.persistence.jpa.PaymentRepository
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CancelPaymentHandlerTest {

    @Test
    fun `APPROVED 결제 취소 성공 시 CANCELED 상태를 반환한다`() {
        val payment = anApprovedPayment(paymentId = "pay-001", merchantId = "merch-001")
        val repository = InMemoryPaymentRepository(payment)
        val gateway = SuccessGateway(Provider.TOSS)
        val handler = buildHandler(repository = repository, gateways = listOf(gateway))

        val result = runBlocking {
            handler.handle(
                cancelCommand(
                    merchantId = "merch-001",
                    paymentId = "pay-001",
                    reason = "고객 요청"
                )
            )
        }

        assertEquals(true, result.success)
        assertEquals(PaymentStatus.CANCELED, result.status)
        assertEquals("pay-001", result.paymentId)
    }

    @Test
    fun `FAILED 결제 취소 시도 시 PaymentNotCancelableException을 던진다`() {
        val payment = aFailedPayment(paymentId = "pay-002", merchantId = "merch-001")
        val repository = InMemoryPaymentRepository(payment)
        val handler = buildHandler(repository = repository)

        assertFailsWith<PaymentNotCancelableException> {
            runBlocking {
                handler.handle(
                    cancelCommand(
                        merchantId = "merch-001",
                        paymentId = "pay-002",
                        reason = "테스트"
                    )
                )
            }
        }
    }

    @Test
    fun `다른 merchantId의 결제 취소 시도 시 PaymentAccessDeniedException을 던진다`() {
        val payment = anApprovedPayment(paymentId = "pay-003", merchantId = "merch-001")
        val repository = InMemoryPaymentRepository(payment)
        val handler = buildHandler(repository = repository)

        assertFailsWith<PaymentAccessDeniedException> {
            runBlocking {
                handler.handle(
                    cancelCommand(
                        merchantId = "merch-intruder",
                        paymentId = "pay-003",
                        reason = "불법 접근"
                    )
                )
            }
        }
    }

    @Test
    fun `존재하지 않는 결제 취소 시도 시 PaymentNotFoundException을 던진다`() {
        val repository = InMemoryPaymentRepository()
        val handler = buildHandler(repository = repository)

        assertFailsWith<PaymentNotFoundException> {
            runBlocking {
                handler.handle(
                    cancelCommand(
                        merchantId = "merch-001",
                        paymentId = "pay-nonexistent",
                        reason = "테스트"
                    )
                )
            }
        }
    }

    @Test
    fun `SANDBOX 환경에서 취소 시 SandboxPaymentSimulator를 사용한다`() {
        val payment = anApprovedPayment(paymentId = "pay-sandbox-001", merchantId = "merch-sandbox")
        val repository = InMemoryPaymentRepository(payment)
        val trackingGateway = TrackingGateway(Provider.TOSS)
        val handler = buildHandler(repository = repository, gateways = listOf(trackingGateway))

        val result = runBlocking {
            handler.handle(
                cancelCommand(
                    merchantId = "merch-sandbox",
                    environment = ApiKeyEnvironment.SANDBOX,
                    paymentId = "pay-sandbox-001",
                    reason = "Sandbox 취소 테스트"
                )
            )
        }

        assertEquals(PaymentStatus.CANCELED, result.status)
        assertEquals(0, trackingGateway.cancelCallCount)
    }
}

// -------------------------------------------------------------------------
// Fixtures & Stubs
// -------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
private fun <T> nullStub(): T = null as T

private fun cancelCommand(
    merchantId: String,
    environment: ApiKeyEnvironment = ApiKeyEnvironment.LIVE,
    paymentId: String,
    reason: String
) = UnifiedPaymentCancelCommand(
    merchantId = merchantId,
    environment = environment,
    paymentId = paymentId,
    reason = reason,
    idempotencyKey = null,
    requestedAt = null
)

private fun anApprovedPayment(paymentId: String, merchantId: String) = Payment(
    paymentId = paymentId,
    merchantId = merchantId,
    orderId = "order-001",
    amount = 10_000L,
    currency = "KRW",
    idempotencyKey = "idem-001",
    requestedAt = Instant.now(),
    status = PaymentStatus.APPROVED,
    approvedProvider = Provider.TOSS,
    providerTxId = "toss-tx-001",
    approvedAt = Instant.now(),
    attempts = emptyList(),
    selectionSummary = SelectionSummary.sandbox(),
    metadata = emptyMap()
)

private fun aFailedPayment(paymentId: String, merchantId: String) = Payment(
    paymentId = paymentId,
    merchantId = merchantId,
    orderId = "order-001",
    amount = 10_000L,
    currency = "KRW",
    idempotencyKey = "idem-001",
    requestedAt = Instant.now(),
    status = PaymentStatus.FAILED,
    approvedProvider = null,
    providerTxId = null,
    approvedAt = null,
    attempts = emptyList(),
    selectionSummary = SelectionSummary.sandbox(),
    failureCode = "CARD_DECLINED",
    failureCategory = FailureCategory.NON_RETRYABLE_BUSINESS,
    failureMessage = "카드 거절",
    metadata = emptyMap()
)

private fun buildHandler(
    repository: PaymentRepository,
    gateways: List<PaymentProviderGateway> = emptyList()
): CancelPaymentHandler {
    val fakePaymentSavePort = FakePaymentSavePortForCancelTest(repository)
    val sandboxSimulator = SandboxPaymentSimulator(fakePaymentSavePort)
    return CancelPaymentHandler(repository, gateways, sandboxSimulator)
}

private class InMemoryPaymentRepository(vararg initialPayments: Payment) : PaymentRepository(
    jpaRepository = nullStub(),
    selectionSummaryJpaRepository = nullStub(),
    queryDslRepository = nullStub(),
    objectMapper = nullStub()
) {
    private val store = ConcurrentHashMap<String, Payment>()

    init {
        initialPayments.forEach { store[it.paymentId] = it }
    }

    override fun save(payment: Payment): Payment {
        store[payment.paymentId] = payment
        return payment
    }

    override fun findById(paymentId: String): Payment? = store[paymentId]
}

private class FakePaymentSavePortForCancelTest(
    private val repository: PaymentRepository
) : PaymentSavePort {
    override fun save(payment: Payment): Payment = repository.save(payment)
    override fun findById(paymentId: String): Payment? = repository.findById(paymentId)
}

private class SuccessGateway(private val provider: Provider) : PaymentProviderGateway {
    override fun supports(provider: Provider): Boolean = this.provider == provider

    override suspend fun approve(command: GatewayApproveCommand): GatewayApproveResult =
        GatewayApproveResult(success = true, provider = provider, providerTxId = "tx-001", approvedAt = Instant.now(), status = PaymentStatus.APPROVED)

    override suspend fun cancel(command: GatewayCancelCommand): GatewayCancelResult =
        GatewayCancelResult(
            success = true,
            provider = provider,
            providerTxId = command.providerTxId,
            canceledAt = Instant.now(),
            status = PaymentStatus.CANCELED
        )

    override suspend fun getPayment(query: GatewayPaymentQuery): GatewayPaymentResult =
        GatewayPaymentResult(success = true, provider = provider, providerTxId = query.providerTxId, status = PaymentStatus.APPROVED)
}

private class TrackingGateway(private val provider: Provider) : PaymentProviderGateway {
    var cancelCallCount = 0

    override fun supports(provider: Provider): Boolean = this.provider == provider

    override suspend fun approve(command: GatewayApproveCommand): GatewayApproveResult =
        GatewayApproveResult(success = true, provider = provider, providerTxId = "tx-001", approvedAt = Instant.now(), status = PaymentStatus.APPROVED)

    override suspend fun cancel(command: GatewayCancelCommand): GatewayCancelResult {
        cancelCallCount++
        return GatewayCancelResult(
            success = true,
            provider = provider,
            providerTxId = command.providerTxId,
            canceledAt = Instant.now(),
            status = PaymentStatus.CANCELED
        )
    }

    override suspend fun getPayment(query: GatewayPaymentQuery): GatewayPaymentResult =
        GatewayPaymentResult(success = true, provider = provider, providerTxId = query.providerTxId, status = PaymentStatus.APPROVED)
}
