package com.pg.ochestration.application.service

import com.pg.ochestration.application.orchestration.ApprovePaymentCommand
import com.pg.ochestration.application.orchestration.PgOrchestrator
import com.pg.ochestration.application.port.out.PaymentIdGeneratorPort
import com.pg.ochestration.application.port.out.PaymentSavePort
import com.pg.ochestration.application.port.out.WebhookDeliveryRepository
import com.pg.ochestration.application.port.out.WebhookEndpointRepository
import com.pg.ochestration.application.port.out.WebhookHttpClient
import com.pg.ochestration.application.port.out.WebhookHttpResponse
import com.pg.ochestration.application.port.out.WebhookSigner
import com.pg.ochestration.application.port.out.WebhookUrlValidationResult
import com.pg.ochestration.application.port.out.WebhookUrlValidator
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.SelectionSummary
import com.pg.ochestration.domain.model.WebhookDelivery
import com.pg.ochestration.domain.model.WebhookDeliveryStatus
import com.pg.ochestration.domain.model.WebhookEndpoint
import com.pg.ochestration.domain.model.WebhookEndpointStatus
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

class WebhookPaymentDispatchIntegrationTest {

    @Test
    fun `결제 승인으로 생성된 delivery를 worker가 발송 완료 처리한다`() {
        val endpointRepository = IntegrationEndpointRepository(listOf(integrationEndpoint()))
        val deliveryRepository = IntegrationDeliveryRepository()
        val paymentRepository = IntegrationPaymentRepository()
        val httpClient = IntegrationRecordingHttpClient()
        val service = UnifiedPaymentService(
            pgOrchestrator = IntegrationPgOrchestrator(),
            paymentRepository = paymentRepository,
            gateways = emptyList(),
            sandboxPaymentSimulator = SandboxPaymentSimulator(IntegrationPaymentSavePort(paymentRepository)),
            webhookPaymentEventPublisher = WebhookPaymentEventPublisher(
                webhookEndpointRepository = endpointRepository,
                webhookDeliveryRepository = deliveryRepository,
                objectMapper = ObjectMapper()
            ),
            transactionTemplate = TransactionTemplate(IntegrationTransactionManager())
        )
        val dispatchService = WebhookDispatchService(
            webhookDeliveryRepository = deliveryRepository,
            webhookEndpointRepository = endpointRepository,
            webhookHttpClient = httpClient,
            webhookSigner = IntegrationSigner(),
            webhookUrlValidator = IntegrationUrlValidator,
            properties = WebhookDispatchProperties(batchSize = 50)
        )

        val payment = runBlocking {
            service.approve(
                principal = MerchantPrincipal("merchant-001", ApiKeyEnvironment.LIVE),
                orderId = "order-001",
                amount = 10_000L,
                currency = "KRW",
                idempotencyKey = null,
                requestedAt = Instant.parse("2026-05-07T00:00:00Z"),
                preferredPrimaryProvider = null,
                metadata = emptyMap()
            )
        }

        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertEquals(listOf(WebhookDeliveryStatus.PENDING), deliveryRepository.store.values.map { it.status })

        dispatchService.dispatchDue(Instant.parse("2026-05-07T00:00:01Z"))

        val sent = deliveryRepository.store.values.single()
        assertEquals(WebhookDeliveryStatus.SENT, sent.status)
        assertEquals(payment.paymentId, sent.paymentId)
        assertEquals(1, httpClient.requests.size)
        assertEquals(sent.deliveryId, httpClient.requests.single().deliveryId)
        assertEquals("signature", httpClient.requests.single().signature)
    }
}

private class IntegrationPgOrchestrator : PgOrchestrator(
    paymentIdGenerator = IntegrationPaymentIdGenerator(),
    providerSelectionPolicy = integrationNullStub(),
    gateways = emptyList(),
    paymentRepository = integrationNullStub()
) {
    override suspend fun approve(
        command: ApprovePaymentCommand,
        savePayment: (Payment) -> Payment
    ): Payment =
        savePayment(
            Payment(
                paymentId = "payment-001",
                merchantId = command.merchantId,
                orderId = command.orderId,
                amount = command.amount,
                currency = command.currency,
                idempotencyKey = command.idempotencyKey,
                requestedAt = command.requestedAt,
                status = PaymentStatus.APPROVED,
                approvedProvider = Provider.TOSS,
                providerTxId = "provider-tx-001",
                approvedAt = command.requestedAt,
                attempts = emptyList(),
                selectionSummary = SelectionSummary.sandbox(),
                metadata = command.metadata
            )
        )
}

private class IntegrationPaymentRepository : PaymentRepository(
    jpaRepository = integrationNullStub(),
    selectionSummaryJpaRepository = integrationNullStub(),
    queryDslRepository = integrationNullStub(),
    objectMapper = integrationNullStub()
) {
    private val store = ConcurrentHashMap<String, Payment>()

    override fun save(payment: Payment): Payment {
        store[payment.paymentId] = payment
        return payment
    }

    override fun findById(paymentId: String): Payment? = store[paymentId]
}

private class IntegrationPaymentSavePort(
    private val repository: PaymentRepository
) : PaymentSavePort {
    override fun save(payment: Payment): Payment = repository.save(payment)
    override fun findById(paymentId: String): Payment? = repository.findById(paymentId)
}

private class IntegrationEndpointRepository(
    endpoints: List<WebhookEndpoint>
) : WebhookEndpointRepository {
    private val store = endpoints.associateBy { it.endpointId }.toMutableMap()

    override fun save(endpoint: WebhookEndpoint): WebhookEndpoint {
        store[endpoint.endpointId] = endpoint
        return endpoint
    }

    override fun findById(endpointId: String): WebhookEndpoint? = store[endpointId]

    override fun findAllByMerchantId(merchantId: String): List<WebhookEndpoint> =
        store.values.filter { it.merchantId == merchantId }

    override fun findActiveByMerchantId(merchantId: String): List<WebhookEndpoint> =
        store.values.filter { it.merchantId == merchantId && it.status == WebhookEndpointStatus.ACTIVE }

    override fun countByMerchantId(merchantId: String): Int =
        store.values.count { it.merchantId == merchantId }

    override fun deactivate(endpointId: String, now: Instant): WebhookEndpoint? {
        val endpoint = store[endpointId] ?: return null
        val deactivated = endpoint.deactivate(now)
        store[endpointId] = deactivated
        return deactivated
    }
}

private class IntegrationDeliveryRepository : WebhookDeliveryRepository {
    val store = linkedMapOf<String, WebhookDelivery>()

    override fun save(delivery: WebhookDelivery): WebhookDelivery {
        store[delivery.deliveryId] = delivery
        return delivery
    }

    override fun saveAll(deliveries: List<WebhookDelivery>): List<WebhookDelivery> {
        deliveries.forEach { save(it) }
        return deliveries
    }

    override fun findDueForDispatch(now: Instant, limit: Int): List<WebhookDelivery> =
        store.values
            .filter { delivery ->
                delivery.status == WebhookDeliveryStatus.PENDING ||
                    (delivery.status == WebhookDeliveryStatus.FAILED && delivery.nextRetryAt != null && delivery.nextRetryAt <= now)
            }
            .take(limit)
}

private class IntegrationRecordingHttpClient : WebhookHttpClient {
    val requests = mutableListOf<IntegrationWebhookRequest>()

    override fun post(endpoint: WebhookEndpoint, delivery: WebhookDelivery, signature: String): WebhookHttpResponse {
        requests += IntegrationWebhookRequest(deliveryId = delivery.deliveryId, signature = signature)
        return WebhookHttpResponse(204)
    }
}

private data class IntegrationWebhookRequest(
    val deliveryId: String,
    val signature: String
)

private class IntegrationSigner : WebhookSigner {
    override fun sign(secret: String, eventId: String, payload: String): String = "signature"
}

private object IntegrationUrlValidator : WebhookUrlValidator {
    override fun validate(url: String): WebhookUrlValidationResult = WebhookUrlValidationResult.allowed()
}

private class IntegrationPaymentIdGenerator : PaymentIdGeneratorPort {
    override fun generate(): String = UUID.randomUUID().toString()
}

private class IntegrationTransactionManager : PlatformTransactionManager {
    override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
        SimpleTransactionStatus()

    override fun commit(status: TransactionStatus) = Unit

    override fun rollback(status: TransactionStatus) = Unit
}

private fun integrationEndpoint() = WebhookEndpoint(
    endpointId = "endpoint-001",
    merchantId = "merchant-001",
    url = "https://merchant.example/webhook",
    signingSecret = "secret",
    status = WebhookEndpointStatus.ACTIVE,
    description = null,
    createdAt = Instant.parse("2026-05-07T00:00:00Z"),
    updatedAt = Instant.parse("2026-05-07T00:00:00Z")
)

@Suppress("UNCHECKED_CAST")
private fun <T> integrationNullStub(): T = null as T
