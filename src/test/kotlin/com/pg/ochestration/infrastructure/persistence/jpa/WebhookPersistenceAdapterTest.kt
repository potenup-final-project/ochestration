package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.domain.model.WebhookDelivery
import com.pg.ochestration.domain.model.WebhookDeliveryStatus
import com.pg.ochestration.domain.model.WebhookEndpoint
import com.pg.ochestration.domain.model.WebhookEndpointStatus
import com.pg.ochestration.domain.model.WebhookEventType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@DataJpaTest
@ActiveProfiles("test")
@Import(WebhookEndpointAdapter::class, WebhookDeliveryAdapter::class)
class WebhookPersistenceAdapterTest {

    @Autowired lateinit var endpointAdapter: WebhookEndpointAdapter
    @Autowired lateinit var deliveryAdapter: WebhookDeliveryAdapter

    @Test
    fun `findActiveByMerchantId는 ACTIVE 엔드포인트만 반환한다`() {
        endpointAdapter.save(anEndpoint(endpointId = "endpoint-active", status = WebhookEndpointStatus.ACTIVE))
        endpointAdapter.save(anEndpoint(endpointId = "endpoint-inactive", status = WebhookEndpointStatus.INACTIVE))

        val activeEndpoints = endpointAdapter.findActiveByMerchantId("merchant-001")

        assertEquals(1, activeEndpoints.size)
        assertEquals("endpoint-active", activeEndpoints.first().endpointId)
    }

    @Test
    fun `countByMerchantId는 상태와 관계없이 가맹점 엔드포인트 수를 반환한다`() {
        endpointAdapter.save(anEndpoint(endpointId = "endpoint-count-1", status = WebhookEndpointStatus.ACTIVE))
        endpointAdapter.save(anEndpoint(endpointId = "endpoint-count-2", status = WebhookEndpointStatus.INACTIVE))

        val count = endpointAdapter.countByMerchantId("merchant-001")

        assertEquals(2, count)
    }

    @Test
    fun `deactivate는 엔드포인트를 삭제하지 않고 INACTIVE 상태로 저장한다`() {
        val now = Instant.parse("2026-05-04T00:01:00Z")
        endpointAdapter.save(anEndpoint(endpointId = "endpoint-deactivate", status = WebhookEndpointStatus.ACTIVE))

        val deactivated = endpointAdapter.deactivate("endpoint-deactivate", now)

        assertNotNull(deactivated)
        assertEquals(WebhookEndpointStatus.INACTIVE, deactivated.status)
        assertEquals(now, deactivated.updatedAt)
        assertEquals(1, endpointAdapter.countByMerchantId("merchant-001"))
    }

    @Test
    fun `delivery를 저장하고 다시 조회할 수 있다`() {
        val saved = deliveryAdapter.save(aDelivery(deliveryId = "delivery-save"))

        val found = deliveryAdapter.findDueForDispatch(Instant.parse("2026-05-04T00:00:10Z"), limit = 10)
            .firstOrNull { it.deliveryId == saved.deliveryId }

        assertNotNull(found)
        assertEquals(WebhookEventType.PAYMENT_APPROVED, found.eventType)
        assertEquals(WebhookDeliveryStatus.PENDING, found.status)
    }

    @Test
    fun `findDueForDispatch는 PENDING과 재시도 시간이 지난 FAILED만 반환한다`() {
        val now = Instant.parse("2026-05-04T00:00:10Z")
        deliveryAdapter.save(aDelivery(deliveryId = "delivery-pending", status = WebhookDeliveryStatus.PENDING))
        deliveryAdapter.save(
            aDelivery(
                deliveryId = "delivery-failed-due",
                status = WebhookDeliveryStatus.FAILED,
                nextRetryAt = now.minusSeconds(1)
            )
        )
        deliveryAdapter.save(
            aDelivery(
                deliveryId = "delivery-failed-future",
                status = WebhookDeliveryStatus.FAILED,
                nextRetryAt = now.plusSeconds(1)
            )
        )
        deliveryAdapter.save(aDelivery(deliveryId = "delivery-sent", status = WebhookDeliveryStatus.SENT))

        val dueIds = deliveryAdapter.findDueForDispatch(now, limit = 10).map { it.deliveryId }

        assertEquals(setOf("delivery-pending", "delivery-failed-due"), dueIds.toSet())
    }

    @Test
    fun `findDueForDispatch는 limit이 1보다 작으면 예외를 던진다`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            deliveryAdapter.findDueForDispatch(Instant.parse("2026-05-04T00:00:10Z"), limit = 0)
        }

        assertEquals("웹훅 dispatch 조회 limit은 1 이상이어야 합니다: limit=0", ex.message)
    }
}

private fun anEndpoint(
    endpointId: String,
    status: WebhookEndpointStatus
) = WebhookEndpoint(
    endpointId = endpointId,
    merchantId = "merchant-001",
    url = "https://merchant.example/webhook",
    signingSecret = "secret",
    status = status,
    description = null,
    createdAt = Instant.parse("2026-05-04T00:00:00Z"),
    updatedAt = Instant.parse("2026-05-04T00:00:00Z")
)

private fun aDelivery(
    deliveryId: String,
    status: WebhookDeliveryStatus = WebhookDeliveryStatus.PENDING,
    nextRetryAt: Instant? = null
) = WebhookDelivery(
    deliveryId = deliveryId,
    endpointId = "endpoint-001",
    merchantId = "merchant-001",
    eventType = WebhookEventType.PAYMENT_APPROVED,
    eventId = "event-$deliveryId",
    paymentId = "payment-001",
    payload = """{"eventId":"event-$deliveryId"}""",
    status = status,
    attemptCount = 0,
    maxAttempts = 5,
    nextRetryAt = nextRetryAt,
    lastAttemptedAt = null,
    lastResponseCode = null,
    lastError = null,
    createdAt = Instant.parse("2026-05-04T00:00:00Z")
)
