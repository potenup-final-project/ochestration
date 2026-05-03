package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.WebhookDeliveryNotRetryableException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WebhookDeliveryTest {

    @Test
    fun `markSent는 SENT 상태로 변경하고 attemptCount를 증가시킨다`() {
        val now = Instant.parse("2026-05-04T00:00:00Z")
        val delivery = aDelivery()

        val sent = delivery.markSent(responseCode = 200, now = now)

        assertEquals(WebhookDeliveryStatus.SENT, sent.status)
        assertEquals(1, sent.attemptCount)
        assertEquals(now, sent.lastAttemptedAt)
        assertEquals(200, sent.lastResponseCode)
        assertNull(sent.nextRetryAt)
        assertNull(sent.lastError)
    }

    @Test
    fun `첫 번째 실패는 FAILED 상태와 1초 뒤 nextRetryAt을 반환한다`() {
        val now = Instant.parse("2026-05-04T00:00:00Z")
        val failed = aDelivery().markFailed(error = "timeout", responseCode = null, now = now)

        assertEquals(WebhookDeliveryStatus.FAILED, failed.status)
        assertEquals(1, failed.attemptCount)
        assertEquals(now.plusSeconds(1), failed.nextRetryAt)
        assertEquals("timeout", failed.lastError)
    }

    @Test
    fun `네 번째 실패는 FAILED 상태와 8초 뒤 nextRetryAt을 반환한다`() {
        val now = Instant.parse("2026-05-04T00:00:00Z")
        val failed = aDelivery(attemptCount = 3).markFailed(error = "server error", responseCode = 500, now = now)

        assertEquals(WebhookDeliveryStatus.FAILED, failed.status)
        assertEquals(4, failed.attemptCount)
        assertEquals(now.plusSeconds(8), failed.nextRetryAt)
        assertEquals(500, failed.lastResponseCode)
    }

    @Test
    fun `다섯 번째 실패는 DEAD 상태가 되고 재시도 시간이 제거된다`() {
        val now = Instant.parse("2026-05-04T00:00:00Z")
        val dead = aDelivery(attemptCount = 4).markFailed(error = "server error", responseCode = 500, now = now)

        assertEquals(WebhookDeliveryStatus.DEAD, dead.status)
        assertEquals(5, dead.attemptCount)
        assertNull(dead.nextRetryAt)
    }

    @Test
    fun `SENT 상태의 delivery는 재시도할 수 없다`() {
        val sent = aDelivery(status = WebhookDeliveryStatus.SENT)

        assertFailsWith<WebhookDeliveryNotRetryableException> {
            sent.markFailed(error = "again", responseCode = null)
        }
    }
}

private fun aDelivery(
    status: WebhookDeliveryStatus = WebhookDeliveryStatus.PENDING,
    attemptCount: Int = 0
) = WebhookDelivery(
    deliveryId = "delivery-001",
    endpointId = "endpoint-001",
    merchantId = "merchant-001",
    eventType = WebhookEventType.PAYMENT_APPROVED,
    eventId = "event-001",
    paymentId = "payment-001",
    payload = """{"eventId":"event-001"}""",
    status = status,
    attemptCount = attemptCount,
    maxAttempts = 5,
    nextRetryAt = null,
    lastAttemptedAt = null,
    lastResponseCode = null,
    lastError = null,
    createdAt = Instant.parse("2026-05-04T00:00:00Z")
)
