package com.pg.ochestration.application.service

import com.pg.ochestration.application.port.out.WebhookDeliveryRepository
import com.pg.ochestration.application.port.out.WebhookEndpointRepository
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.SelectionSummary
import com.pg.ochestration.domain.model.WebhookDelivery
import com.pg.ochestration.domain.model.WebhookDeliveryStatus
import com.pg.ochestration.domain.model.WebhookEndpoint
import com.pg.ochestration.domain.model.WebhookEndpointStatus
import com.pg.ochestration.domain.model.WebhookEventType
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebhookPaymentEventPublisherTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `APPROVED 결제는 활성 endpoint마다 동일 payload의 PENDING delivery를 생성한다`() {
        val endpointRepository = PublisherFakeEndpointRepository(
            listOf(
                publisherEndpoint(endpointId = "endpoint-001", status = WebhookEndpointStatus.ACTIVE),
                publisherEndpoint(endpointId = "endpoint-002", status = WebhookEndpointStatus.ACTIVE),
                publisherEndpoint(endpointId = "endpoint-inactive", status = WebhookEndpointStatus.INACTIVE)
            )
        )
        val deliveryRepository = PublisherFakeDeliveryRepository()
        val publisher = WebhookPaymentEventPublisher(endpointRepository, deliveryRepository, objectMapper)

        val deliveries = publisher.publish(
            payment = publisherPayment(status = PaymentStatus.APPROVED),
            eventType = WebhookEventType.PAYMENT_APPROVED
        )

        assertEquals(2, deliveries.size)
        assertEquals(setOf("endpoint-001", "endpoint-002"), deliveries.map { it.endpointId }.toSet())
        assertTrue(deliveries.all { it.status == WebhookDeliveryStatus.PENDING })
        assertTrue(deliveries.all { it.eventType == WebhookEventType.PAYMENT_APPROVED })
        assertEquals(1, deliveries.map { it.eventId }.toSet().size)
        assertEquals(1, deliveries.map { it.payload }.toSet().size)

        val payload = payloadMap(deliveries.first().payload)
        assertEquals("PAYMENT.APPROVED", payload["eventType"])
        assertEquals(deliveries.first().eventId, payload["eventId"])
        assertEquals("payment-001", payload["paymentId"])
        assertEquals("merchant-001", payload["merchantId"])
        assertEquals("KRW", payload["currency"])
        assertEquals("APPROVED", payload["status"])
    }

    @Test
    fun `FAILED 결제는 PAYMENT_FAILED delivery를 생성한다`() {
        val deliveryRepository = PublisherFakeDeliveryRepository()
        val publisher = WebhookPaymentEventPublisher(
            PublisherFakeEndpointRepository(listOf(publisherEndpoint())),
            deliveryRepository,
            objectMapper
        )

        val deliveries = publisher.publish(
            payment = publisherPayment(status = PaymentStatus.FAILED),
            eventType = WebhookEventType.PAYMENT_FAILED
        )

        assertEquals(1, deliveries.size)
        assertEquals(WebhookEventType.PAYMENT_FAILED, deliveries.first().eventType)
        assertEquals("PAYMENT.FAILED", payloadMap(deliveries.first().payload)["eventType"])
    }

    @Test
    fun `CANCELED 결제는 PAYMENT_CANCELED delivery를 생성한다`() {
        val publisher = WebhookPaymentEventPublisher(
            PublisherFakeEndpointRepository(listOf(publisherEndpoint())),
            PublisherFakeDeliveryRepository(),
            objectMapper
        )

        val deliveries = publisher.publish(
            payment = publisherPayment(status = PaymentStatus.CANCELED),
            eventType = WebhookEventType.PAYMENT_CANCELED
        )

        assertEquals(1, deliveries.size)
        assertEquals(WebhookEventType.PAYMENT_CANCELED, deliveries.first().eventType)
    }

    @Test
    fun `활성 endpoint가 없으면 delivery를 생성하지 않는다`() {
        val deliveryRepository = PublisherFakeDeliveryRepository()
        val publisher = WebhookPaymentEventPublisher(
            PublisherFakeEndpointRepository(listOf(publisherEndpoint(status = WebhookEndpointStatus.INACTIVE))),
            deliveryRepository,
            objectMapper
        )

        val deliveries = publisher.publish(
            payment = publisherPayment(status = PaymentStatus.APPROVED),
            eventType = WebhookEventType.PAYMENT_APPROVED
        )

        assertEquals(emptyList(), deliveries)
        assertEquals(emptyList(), deliveryRepository.saved)
    }

    @Test
    fun `명시한 이벤트 타입으로 delivery를 생성한다`() {
        val deliveryRepository = PublisherFakeDeliveryRepository()
        val publisher = WebhookPaymentEventPublisher(
            PublisherFakeEndpointRepository(listOf(publisherEndpoint())),
            deliveryRepository,
            objectMapper
        )

        val deliveries = publisher.publish(
            payment = publisherPayment(status = PaymentStatus.FAILED),
            eventType = WebhookEventType.PAYMENT_APPROVED
        )

        assertEquals(WebhookEventType.PAYMENT_APPROVED, deliveries.first().eventType)
        assertEquals("PAYMENT.APPROVED", payloadMap(deliveries.first().payload)["eventType"])
    }

    private fun payloadMap(payload: String): Map<String, Any?> =
        objectMapper.readValue(payload, object : TypeReference<Map<String, Any?>>() {})
}

private class PublisherFakeEndpointRepository(
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

private class PublisherFakeDeliveryRepository : WebhookDeliveryRepository {
    val saved = mutableListOf<WebhookDelivery>()

    override fun save(delivery: WebhookDelivery): WebhookDelivery {
        saved += delivery
        return delivery
    }

    override fun saveAll(deliveries: List<WebhookDelivery>): List<WebhookDelivery> {
        saved += deliveries
        return deliveries
    }

    override fun findDueForDispatch(now: Instant, limit: Int): List<WebhookDelivery> =
        saved.filter { it.status == WebhookDeliveryStatus.PENDING }
}

private fun publisherEndpoint(
    endpointId: String = "endpoint-001",
    status: WebhookEndpointStatus = WebhookEndpointStatus.ACTIVE
) = WebhookEndpoint(
    endpointId = endpointId,
    merchantId = "merchant-001",
    url = "https://merchant.example/webhook",
    signingSecret = "secret",
    status = status,
    description = null,
    createdAt = Instant.parse("2026-05-05T00:00:00Z"),
    updatedAt = Instant.parse("2026-05-05T00:00:00Z")
)

private fun publisherPayment(status: PaymentStatus) = Payment(
    paymentId = "payment-001",
    merchantId = "merchant-001",
    orderId = "order-001",
    amount = 10_000L,
    currency = "KRW",
    idempotencyKey = "idem-001",
    requestedAt = Instant.parse("2026-05-05T00:00:00Z"),
    status = status,
    approvedProvider = Provider.TOSS.takeIf { status == PaymentStatus.APPROVED || status == PaymentStatus.CANCELED },
    providerTxId = "provider-tx-001".takeIf { status == PaymentStatus.APPROVED || status == PaymentStatus.CANCELED },
    approvedAt = Instant.parse("2026-05-05T00:00:01Z").takeIf { status == PaymentStatus.APPROVED || status == PaymentStatus.CANCELED },
    canceledAt = Instant.parse("2026-05-05T00:00:02Z").takeIf { status == PaymentStatus.CANCELED },
    attempts = emptyList(),
    selectionSummary = SelectionSummary.sandbox(),
    metadata = emptyMap()
)
