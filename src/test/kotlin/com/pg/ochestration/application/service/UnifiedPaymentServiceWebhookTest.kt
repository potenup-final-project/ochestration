package com.pg.ochestration.application.service

import com.pg.ochestration.application.orchestration.ApprovePaymentCommand
import com.pg.ochestration.application.orchestration.PgOrchestrator
import com.pg.ochestration.application.port.out.GatewayApproveCommand
import com.pg.ochestration.application.port.out.GatewayApproveResult
import com.pg.ochestration.application.port.out.GatewayCancelCommand
import com.pg.ochestration.application.port.out.GatewayCancelResult
import com.pg.ochestration.application.port.out.GatewayPaymentQuery
import com.pg.ochestration.application.port.out.GatewayPaymentResult
import com.pg.ochestration.application.port.out.PaymentIdGeneratorPort
import com.pg.ochestration.application.port.out.PaymentProviderGateway
import com.pg.ochestration.application.port.out.PaymentSavePort
import com.pg.ochestration.application.port.out.WebhookDeliveryRepository
import com.pg.ochestration.application.port.out.WebhookEndpointRepository
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.SelectionSummary
import com.pg.ochestration.domain.model.WebhookDelivery
import com.pg.ochestration.domain.model.WebhookEndpoint
import com.pg.ochestration.domain.model.WebhookEventType
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import com.pg.ochestration.infrastructure.persistence.jpa.PaymentRepository
import kotlinx.coroutines.runBlocking
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UnifiedPaymentServiceWebhookTest {

    @Test
    fun `LIVE 승인 결과에 대한 웹훅 이벤트를 발행한다`() {
        val publisher = HookTrackingWebhookPublisher()
        val service = hookService(
            pgOrchestrator = HookPgOrchestrator(hookPayment(status = PaymentStatus.APPROVED)),
            publisher = publisher
        )

        val payment = runBlocking {
            service.approve(
                principal = hookPrincipal(ApiKeyEnvironment.LIVE),
                orderId = "order-001",
                amount = 10_000L,
                currency = "KRW",
                idempotencyKey = null,
                requestedAt = null,
                preferredPrimaryProvider = null,
                metadata = emptyMap()
            )
        }

        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertEquals(listOf(PaymentStatus.APPROVED), publisher.published.map { it.payment.status })
        assertEquals(listOf(WebhookEventType.PAYMENT_APPROVED), publisher.published.map { it.eventType })
    }

    @Test
    fun `SANDBOX 승인 실패 결과에 대한 웹훅 이벤트를 발행한다`() {
        val publisher = HookTrackingWebhookPublisher()
        val service = hookService(publisher = publisher)

        val payment = runBlocking {
            service.approve(
                principal = hookPrincipal(ApiKeyEnvironment.SANDBOX),
                orderId = "order-fail-001",
                amount = 10_000L,
                currency = "KRW",
                idempotencyKey = null,
                requestedAt = null,
                preferredPrimaryProvider = null,
                metadata = emptyMap()
            )
        }

        assertEquals(PaymentStatus.FAILED, payment.status)
        assertEquals(listOf(PaymentStatus.FAILED), publisher.published.map { it.payment.status })
        assertEquals(listOf(WebhookEventType.PAYMENT_FAILED), publisher.published.map { it.eventType })
    }

    @Test
    fun `승인 결과가 발행 대상 상태가 아니면 웹훅 이벤트를 발행하지 않는다`() {
        val publisher = HookTrackingWebhookPublisher()
        val service = hookService(
            pgOrchestrator = HookPgOrchestrator(hookPayment(status = PaymentStatus.READY)),
            publisher = publisher
        )

        val payment = runBlocking {
            service.approve(
                principal = hookPrincipal(ApiKeyEnvironment.LIVE),
                orderId = "order-001",
                amount = 10_000L,
                currency = "KRW",
                idempotencyKey = null,
                requestedAt = null,
                preferredPrimaryProvider = null,
                metadata = emptyMap()
            )
        }

        assertEquals(PaymentStatus.READY, payment.status)
        assertEquals(emptyList(), publisher.published)
    }

    @Test
    fun `LIVE 취소 성공 결과에 대한 웹훅 이벤트를 발행한다`() {
        val repository = HookPaymentRepository(hookPayment(status = PaymentStatus.APPROVED))
        val publisher = HookTrackingWebhookPublisher()
        val service = hookService(
            paymentRepository = repository,
            gateways = listOf(HookSuccessGateway()),
            publisher = publisher
        )

        val response = runBlocking {
            service.cancel(
                principal = hookPrincipal(ApiKeyEnvironment.LIVE),
                paymentId = "payment-001",
                reason = "고객 요청",
                idempotencyKey = null,
                requestedAt = null
            )
        }

        assertEquals(PaymentStatus.CANCELED, response.status)
        assertEquals(listOf(PaymentStatus.CANCELED), publisher.published.map { it.payment.status })
        assertEquals(listOf(WebhookEventType.PAYMENT_CANCELED), publisher.published.map { it.eventType })
    }

    @Test
    fun `LIVE 취소 실패 결과는 웹훅 이벤트를 발행하지 않는다`() {
        val repository = HookPaymentRepository(hookPayment(status = PaymentStatus.APPROVED))
        val publisher = HookTrackingWebhookPublisher()
        val service = hookService(
            paymentRepository = repository,
            gateways = listOf(HookFailedCancelGateway()),
            publisher = publisher
        )

        val response = runBlocking {
            service.cancel(
                principal = hookPrincipal(ApiKeyEnvironment.LIVE),
                paymentId = "payment-001",
                reason = "고객 요청",
                idempotencyKey = null,
                requestedAt = null
            )
        }

        assertEquals(PaymentStatus.FAILED, response.status)
        assertEquals(emptyList(), publisher.published)
    }

    @Test
    fun `웹훅 이벤트 발행 실패는 결제 저장 트랜잭션을 롤백한다`() {
        val transactionManager = RecordingTransactionManager()
        val service = hookService(
            pgOrchestrator = HookPgOrchestrator(hookPayment(status = PaymentStatus.APPROVED)),
            publisher = HookFailingWebhookPublisher(),
            transactionManager = transactionManager
        )

        assertFailsWith<IllegalStateException> {
            runBlocking {
            service.approve(
                principal = hookPrincipal(ApiKeyEnvironment.LIVE),
                orderId = "order-001",
                amount = 10_000L,
                currency = "KRW",
                idempotencyKey = null,
                requestedAt = null,
                preferredPrimaryProvider = null,
                metadata = emptyMap()
            )
            }
        }

        assertEquals(0, transactionManager.commits)
        assertEquals(1, transactionManager.rollbacks)
    }
}

private fun hookService(
    pgOrchestrator: PgOrchestrator = HookPgOrchestrator(hookPayment(status = PaymentStatus.APPROVED)),
    paymentRepository: PaymentRepository = HookPaymentRepository(),
    gateways: List<PaymentProviderGateway> = emptyList(),
    publisher: WebhookPaymentEventPublisher = HookTrackingWebhookPublisher(),
    transactionManager: PlatformTransactionManager = RecordingTransactionManager()
): UnifiedPaymentService {
    val paymentSavePort = HookPaymentSavePort(paymentRepository)
    return UnifiedPaymentService(
        pgOrchestrator = pgOrchestrator,
        paymentRepository = paymentRepository,
        gateways = gateways,
        sandboxPaymentSimulator = SandboxPaymentSimulator(paymentSavePort),
        webhookPaymentEventPublisher = publisher,
        transactionTemplate = TransactionTemplate(transactionManager)
    )
}

private class HookTrackingWebhookPublisher : WebhookPaymentEventPublisher(
    webhookEndpointRepository = HookEndpointRepository(),
    webhookDeliveryRepository = HookDeliveryRepository(),
    objectMapper = ObjectMapper()
) {
    val published = mutableListOf<PublishedWebhook>()

    override fun publish(payment: Payment, eventType: WebhookEventType): List<WebhookDelivery> {
        published += PublishedWebhook(payment = payment, eventType = eventType)
        return emptyList()
    }
}

private class HookFailingWebhookPublisher : WebhookPaymentEventPublisher(
    webhookEndpointRepository = HookEndpointRepository(),
    webhookDeliveryRepository = HookDeliveryRepository(),
    objectMapper = ObjectMapper()
) {
    override fun publish(payment: Payment, eventType: WebhookEventType): List<WebhookDelivery> {
        error("테스트 웹훅 실패")
    }
}

private data class PublishedWebhook(
    val payment: Payment,
    val eventType: WebhookEventType
)

private class HookPgOrchestrator(
    private val payment: Payment
) : PgOrchestrator(
    paymentIdGenerator = HookPaymentIdGenerator(),
    providerSelectionPolicy = hookNullStub(),
    gateways = emptyList(),
    paymentRepository = hookNullStub()
) {
    override suspend fun approve(
        command: ApprovePaymentCommand,
        savePayment: (Payment) -> Payment
    ): Payment =
        savePayment(
            payment.copy(
                merchantId = command.merchantId,
                orderId = command.orderId,
                amount = command.amount,
                currency = command.currency,
                idempotencyKey = command.idempotencyKey,
                requestedAt = command.requestedAt
            )
        )
}

private class HookPaymentRepository(vararg initialPayments: Payment) : PaymentRepository(
    jpaRepository = hookNullStub(),
    selectionSummaryJpaRepository = hookNullStub(),
    queryDslRepository = hookNullStub(),
    objectMapper = hookNullStub()
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

private class HookPaymentSavePort(
    private val repository: PaymentRepository
) : PaymentSavePort {
    override fun save(payment: Payment): Payment = repository.save(payment)

    override fun findById(paymentId: String): Payment? = repository.findById(paymentId)
}

private class HookSuccessGateway : PaymentProviderGateway {
    override fun supports(provider: Provider): Boolean = provider == Provider.TOSS

    override suspend fun approve(command: GatewayApproveCommand): GatewayApproveResult =
        GatewayApproveResult(
            success = true,
            provider = Provider.TOSS,
            providerTxId = "provider-tx-001",
            approvedAt = Instant.now(),
            status = PaymentStatus.APPROVED
        )

    override suspend fun cancel(command: GatewayCancelCommand): GatewayCancelResult =
        GatewayCancelResult(
            success = true,
            provider = Provider.TOSS,
            providerTxId = command.providerTxId,
            canceledAt = Instant.now(),
            status = PaymentStatus.CANCELED
        )

    override suspend fun getPayment(query: GatewayPaymentQuery): GatewayPaymentResult =
        GatewayPaymentResult(
            success = true,
            provider = Provider.TOSS,
            providerTxId = query.providerTxId,
            status = PaymentStatus.APPROVED
        )
}

private class HookFailedCancelGateway : PaymentProviderGateway {
    override fun supports(provider: Provider): Boolean = provider == Provider.TOSS

    override suspend fun approve(command: GatewayApproveCommand): GatewayApproveResult =
        GatewayApproveResult(
            success = true,
            provider = Provider.TOSS,
            providerTxId = "provider-tx-001",
            approvedAt = Instant.now(),
            status = PaymentStatus.APPROVED
        )

    override suspend fun cancel(command: GatewayCancelCommand): GatewayCancelResult =
        GatewayCancelResult(
            success = false,
            provider = Provider.TOSS,
            providerTxId = command.providerTxId,
            canceledAt = null,
            status = PaymentStatus.FAILED
        )

    override suspend fun getPayment(query: GatewayPaymentQuery): GatewayPaymentResult =
        GatewayPaymentResult(
            success = false,
            provider = Provider.TOSS,
            providerTxId = query.providerTxId,
            status = PaymentStatus.FAILED
        )
}

private class HookEndpointRepository : WebhookEndpointRepository {
    override fun save(endpoint: WebhookEndpoint): WebhookEndpoint = endpoint
    override fun findById(endpointId: String): WebhookEndpoint? = null
    override fun findAllByMerchantId(merchantId: String): List<WebhookEndpoint> = emptyList()
    override fun findActiveByMerchantId(merchantId: String): List<WebhookEndpoint> = emptyList()
    override fun countByMerchantId(merchantId: String): Int = 0
    override fun deactivate(endpointId: String, now: Instant): WebhookEndpoint? = null
}

private class HookDeliveryRepository : WebhookDeliveryRepository {
    override fun save(delivery: WebhookDelivery): WebhookDelivery = delivery
    override fun saveAll(deliveries: List<WebhookDelivery>): List<WebhookDelivery> = deliveries
    override fun findDueForDispatch(now: Instant, limit: Int): List<WebhookDelivery> = emptyList()
}

private class HookPaymentIdGenerator : PaymentIdGeneratorPort {
    override fun generate(): String = UUID.randomUUID().toString()
}

private class RecordingTransactionManager : PlatformTransactionManager {
    var commits = 0
    var rollbacks = 0

    override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
        SimpleTransactionStatus()

    override fun commit(status: TransactionStatus) {
        commits += 1
    }

    override fun rollback(status: TransactionStatus) {
        rollbacks += 1
    }
}

private fun hookPrincipal(environment: ApiKeyEnvironment) = MerchantPrincipal(
    merchantId = "merchant-001",
    environment = environment
)

private fun hookPayment(status: PaymentStatus): Payment {
    val now = Instant.parse("2026-05-05T00:00:00Z")
    return Payment(
        paymentId = "payment-001",
        merchantId = "merchant-001",
        orderId = "order-001",
        amount = 10_000L,
        currency = "KRW",
        idempotencyKey = "idem-001",
        requestedAt = now,
        status = status,
        approvedProvider = Provider.TOSS.takeIf { status == PaymentStatus.APPROVED || status == PaymentStatus.CANCELED },
        providerTxId = "provider-tx-001".takeIf { status == PaymentStatus.APPROVED || status == PaymentStatus.CANCELED },
        approvedAt = now.takeIf { status == PaymentStatus.APPROVED || status == PaymentStatus.CANCELED },
        canceledAt = now.takeIf { status == PaymentStatus.CANCELED },
        attempts = emptyList(),
        selectionSummary = SelectionSummary.sandbox(),
        cancelReason = "고객 요청".takeIf { status == PaymentStatus.CANCELED }
    )
}

@Suppress("UNCHECKED_CAST")
private fun <T> hookNullStub(): T = null as T
