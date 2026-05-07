package com.pg.ochestration.application.service

import com.pg.ochestration.application.port.out.WebhookDeliveryRepository
import com.pg.ochestration.application.port.out.WebhookEndpointRepository
import com.pg.ochestration.application.port.out.WebhookHttpClient
import com.pg.ochestration.application.port.out.WebhookHttpResponse
import com.pg.ochestration.application.port.out.WebhookSigner
import com.pg.ochestration.application.port.out.WebhookUrlValidationResult
import com.pg.ochestration.application.port.out.WebhookUrlValidator
import com.pg.ochestration.domain.model.WebhookDelivery
import com.pg.ochestration.domain.model.WebhookDeliveryStatus
import com.pg.ochestration.domain.model.WebhookEndpoint
import com.pg.ochestration.domain.model.WebhookEndpointStatus
import com.pg.ochestration.domain.model.WebhookEventType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebhookDispatchServiceTest {

    @Test
    fun `2xx 응답이면 delivery를 SENT로 저장한다`() {
        val now = Instant.parse("2026-05-04T00:00:00Z")
        val delivery = aDelivery()
        val deliveryRepository = FakeDeliveryRepository(listOf(delivery))
        val service = aService(
            deliveryRepository = deliveryRepository,
            httpClient = FakeHttpClient(WebhookHttpResponse(204))
        )

        service.dispatchDue(now)

        val saved = deliveryRepository.saved.single()
        assertEquals(WebhookDeliveryStatus.SENT, saved.status)
        assertEquals(1, saved.attemptCount)
        assertEquals(204, saved.lastResponseCode)
        assertNull(saved.nextRetryAt)
    }

    @Test
    fun `비 2xx 응답이면 delivery를 FAILED로 저장하고 재시도 시간을 설정한다`() {
        val now = Instant.parse("2026-05-04T00:00:00Z")
        val deliveryRepository = FakeDeliveryRepository(listOf(aDelivery()))
        val service = aService(
            deliveryRepository = deliveryRepository,
            httpClient = FakeHttpClient(WebhookHttpResponse(500))
        )

        service.dispatchDue(now)

        val saved = deliveryRepository.saved.single()
        assertEquals(WebhookDeliveryStatus.FAILED, saved.status)
        assertEquals(1, saved.attemptCount)
        assertEquals(500, saved.lastResponseCode)
        assertEquals(now.plusSeconds(1), saved.nextRetryAt)
    }

    @Test
    fun `마지막 재시도 실패는 DEAD로 저장한다`() {
        val now = Instant.parse("2026-05-04T00:00:00Z")
        val deliveryRepository = FakeDeliveryRepository(listOf(aDelivery(attemptCount = 4)))
        val service = aService(
            deliveryRepository = deliveryRepository,
            httpClient = FakeHttpClient(WebhookHttpResponse(500))
        )

        service.dispatchDue(now)

        val saved = deliveryRepository.saved.single()
        assertEquals(WebhookDeliveryStatus.DEAD, saved.status)
        assertEquals(5, saved.attemptCount)
        assertNull(saved.nextRetryAt)
    }

    @Test
    fun `하나의 발송 실패가 다음 delivery 처리를 막지 않는다`() {
        val now = Instant.parse("2026-05-04T00:00:00Z")
        val first = aDelivery(deliveryId = "delivery-001")
        val second = aDelivery(deliveryId = "delivery-002")
        val deliveryRepository = FakeDeliveryRepository(listOf(first, second))
        val service = aService(
            deliveryRepository = deliveryRepository,
            httpClient = SequenceHttpClient(
                RuntimeException("timeout"),
                WebhookHttpResponse(200)
            )
        )

        service.dispatchDue(now)

        assertEquals(WebhookDeliveryStatus.FAILED, deliveryRepository.saved[0].status)
        assertEquals(WebhookDeliveryStatus.SENT, deliveryRepository.saved[1].status)
    }

    @Test
    fun `발송 직전 URL 보안 검증 실패는 DEAD로 저장하고 HTTP 요청을 보내지 않는다`() {
        val now = Instant.parse("2026-05-04T00:00:00Z")
        val deliveryRepository = FakeDeliveryRepository(listOf(aDelivery()))
        val httpClient = RecordingHttpClient()
        val service = aService(
            deliveryRepository = deliveryRepository,
            httpClient = httpClient,
            urlValidator = FakeUrlValidator(WebhookUrlValidationResult.blocked("내부망 주소"))
        )

        service.dispatchDue(now)

        val saved = deliveryRepository.saved.single()
        assertEquals(WebhookDeliveryStatus.DEAD, saved.status)
        assertEquals(1, saved.attemptCount)
        assertEquals(0, httpClient.postCount)
    }

    @Test
    fun `비활성 endpoint는 DEAD로 저장하고 HTTP 요청을 보내지 않는다`() {
        val now = Instant.parse("2026-05-04T00:00:00Z")
        val deliveryRepository = FakeDeliveryRepository(listOf(aDelivery()))
        val httpClient = RecordingHttpClient()
        val service = aService(
            deliveryRepository = deliveryRepository,
            httpClient = httpClient,
            endpoint = aEndpoint(status = WebhookEndpointStatus.INACTIVE)
        )

        service.dispatchDue(now)

        val saved = deliveryRepository.saved.single()
        assertEquals(WebhookDeliveryStatus.DEAD, saved.status)
        assertEquals(1, saved.attemptCount)
        assertEquals(0, httpClient.postCount)
    }

    private fun aService(
        deliveryRepository: FakeDeliveryRepository,
        httpClient: WebhookHttpClient,
        urlValidator: WebhookUrlValidator = FakeUrlValidator(WebhookUrlValidationResult.allowed()),
        endpoint: WebhookEndpoint = aEndpoint()
    ) = WebhookDispatchService(
        webhookDeliveryRepository = deliveryRepository,
        webhookEndpointRepository = FakeEndpointRepository(endpoint),
        webhookHttpClient = httpClient,
        webhookSigner = FakeSigner(),
        webhookUrlValidator = urlValidator,
        properties = WebhookDispatchProperties(batchSize = 50)
    )
}

private class FakeDeliveryRepository(
    private val dueDeliveries: List<WebhookDelivery>
) : WebhookDeliveryRepository {
    val saved = mutableListOf<WebhookDelivery>()

    override fun save(delivery: WebhookDelivery): WebhookDelivery {
        saved += delivery
        return delivery
    }

    override fun saveAll(deliveries: List<WebhookDelivery>): List<WebhookDelivery> = deliveries

    override fun findDueForDispatch(now: Instant, limit: Int): List<WebhookDelivery> =
        dueDeliveries.take(limit)
}

private class FakeEndpointRepository(
    private val endpoint: WebhookEndpoint
) : WebhookEndpointRepository {
    override fun save(endpoint: WebhookEndpoint): WebhookEndpoint = endpoint
    override fun findById(endpointId: String): WebhookEndpoint? = endpoint.takeIf { it.endpointId == endpointId }
    override fun findAllByMerchantId(merchantId: String): List<WebhookEndpoint> = listOf(endpoint)
    override fun findActiveByMerchantId(merchantId: String): List<WebhookEndpoint> = listOf(endpoint)
    override fun countByMerchantId(merchantId: String): Int = 1
    override fun deactivate(endpointId: String, now: Instant): WebhookEndpoint? = endpoint.deactivate(now)
}

private class FakeSigner : WebhookSigner {
    override fun sign(secret: String, eventId: String, payload: String): String = "signature"
}

private class FakeUrlValidator(
    private val result: WebhookUrlValidationResult
) : WebhookUrlValidator {
    override fun validate(url: String): WebhookUrlValidationResult = result
}

private class FakeHttpClient(
    private val response: WebhookHttpResponse
) : WebhookHttpClient {
    override fun post(endpoint: WebhookEndpoint, delivery: WebhookDelivery, signature: String): WebhookHttpResponse =
        response
}

private class SequenceHttpClient(
    private vararg val results: Any
) : WebhookHttpClient {
    private var index = 0

    override fun post(endpoint: WebhookEndpoint, delivery: WebhookDelivery, signature: String): WebhookHttpResponse {
        val result = results[index++]
        if (result is RuntimeException) throw result
        return result as WebhookHttpResponse
    }
}

private class RecordingHttpClient : WebhookHttpClient {
    var postCount: Int = 0
        private set

    override fun post(endpoint: WebhookEndpoint, delivery: WebhookDelivery, signature: String): WebhookHttpResponse {
        postCount += 1
        return WebhookHttpResponse(200)
    }
}

private fun aEndpoint(
    status: WebhookEndpointStatus = WebhookEndpointStatus.ACTIVE
) = WebhookEndpoint(
    endpointId = "endpoint-001",
    merchantId = "merchant-001",
    url = "https://merchant.example/webhook",
    signingSecret = "secret",
    status = status,
    description = null,
    createdAt = Instant.parse("2026-05-04T00:00:00Z"),
    updatedAt = Instant.parse("2026-05-04T00:00:00Z")
)

private fun aDelivery(
    deliveryId: String = "delivery-001",
    attemptCount: Int = 0
) = WebhookDelivery(
    deliveryId = deliveryId,
    endpointId = "endpoint-001",
    merchantId = "merchant-001",
    eventType = WebhookEventType.PAYMENT_APPROVED,
    eventId = "event-$deliveryId",
    paymentId = "payment-001",
    payload = """{"eventId":"event-$deliveryId"}""",
    status = WebhookDeliveryStatus.PENDING,
    attemptCount = attemptCount,
    maxAttempts = 5,
    nextRetryAt = null,
    lastAttemptedAt = null,
    lastResponseCode = null,
    lastError = null,
    createdAt = Instant.parse("2026-05-04T00:00:00Z")
)
