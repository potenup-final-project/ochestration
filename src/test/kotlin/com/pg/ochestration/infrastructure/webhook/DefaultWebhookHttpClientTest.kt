package com.pg.ochestration.infrastructure.webhook

import com.pg.ochestration.domain.model.WebhookDelivery
import com.pg.ochestration.domain.model.WebhookDeliveryStatus
import com.pg.ochestration.domain.model.WebhookEndpoint
import com.pg.ochestration.domain.model.WebhookEndpointStatus
import com.pg.ochestration.domain.model.WebhookEventType
import com.pg.ochestration.application.service.WebhookDispatchProperties
import com.pg.ochestration.infrastructure.webhook.DefaultWebhookHttpClient.Companion.WEBHOOK_DELIVERY_ID_HEADER
import com.pg.ochestration.infrastructure.webhook.DefaultWebhookHttpClient.Companion.WEBHOOK_EVENT_HEADER
import com.pg.ochestration.infrastructure.webhook.DefaultWebhookHttpClient.Companion.WEBHOOK_EVENT_ID_HEADER
import com.pg.ochestration.infrastructure.webhook.DefaultWebhookHttpClient.Companion.WEBHOOK_SIGNATURE_HEADER
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultWebhookHttpClientTest {

    @Test
    fun `post는 웹훅 헤더와 payload를 전송하고 응답 코드를 반환한다`() {
        val captured = AtomicReference<CapturedRequest>()
        val server = startServer(statusCode = 204, captured = captured)
        try {
            val client = DefaultWebhookHttpClient(WebhookDispatchProperties(connectTimeoutMs = 1_000, readTimeoutMs = 1_000))
            val response = client.post(
                endpoint = aEndpoint("http://localhost:${server.address.port}/webhook"),
                delivery = aDelivery(),
                signature = "signature"
            )

            assertEquals(204, response.statusCode)
            val request = captured.get()
            assertEquals("sha256=signature", request.headers[WEBHOOK_SIGNATURE_HEADER.lowercase()])
            assertEquals("PAYMENT.APPROVED", request.headers[WEBHOOK_EVENT_HEADER.lowercase()])
            assertEquals("event-001", request.headers[WEBHOOK_EVENT_ID_HEADER.lowercase()])
            assertEquals("delivery-001", request.headers[WEBHOOK_DELIVERY_ID_HEADER.lowercase()])
            assertEquals("""{"eventId":"event-001"}""", request.body)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `post는 비 2xx 응답도 예외 대신 상태 코드로 반환한다`() {
        val server = startServer(statusCode = 500, captured = AtomicReference())
        try {
            val client = DefaultWebhookHttpClient(WebhookDispatchProperties(connectTimeoutMs = 1_000, readTimeoutMs = 1_000))

            val response = client.post(
                endpoint = aEndpoint("http://localhost:${server.address.port}/webhook"),
                delivery = aDelivery(),
                signature = "signature"
            )

            assertEquals(500, response.statusCode)
        } finally {
            server.stop(0)
        }
    }
}

private data class CapturedRequest(
    val headers: Map<String, String>,
    val body: String
)

private fun startServer(
    statusCode: Int,
    captured: AtomicReference<CapturedRequest>
): HttpServer {
    val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
    server.createContext("/webhook") { exchange ->
        val body = exchange.requestBody.bufferedReader().readText()
        captured.set(
            CapturedRequest(
                headers = exchange.requestHeaders.mapKeys { it.key.lowercase() }.mapValues { it.value.first() },
                body = body
            )
        )
        exchange.sendResponseHeaders(statusCode, -1)
        exchange.close()
    }
    server.start()
    return server
}

private fun aEndpoint(url: String) = WebhookEndpoint(
    endpointId = "endpoint-001",
    merchantId = "merchant-001",
    url = url,
    signingSecret = "secret",
    status = WebhookEndpointStatus.ACTIVE,
    description = null,
    createdAt = Instant.parse("2026-05-04T00:00:00Z"),
    updatedAt = Instant.parse("2026-05-04T00:00:00Z")
)

private fun aDelivery() = WebhookDelivery(
    deliveryId = "delivery-001",
    endpointId = "endpoint-001",
    merchantId = "merchant-001",
    eventType = WebhookEventType.PAYMENT_APPROVED,
    eventId = "event-001",
    paymentId = "payment-001",
    payload = """{"eventId":"event-001"}""",
    status = WebhookDeliveryStatus.PENDING,
    attemptCount = 0,
    maxAttempts = 5,
    nextRetryAt = null,
    lastAttemptedAt = null,
    lastResponseCode = null,
    lastError = null,
    createdAt = Instant.parse("2026-05-04T00:00:00Z")
)
