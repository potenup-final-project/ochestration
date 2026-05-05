package com.pg.ochestration.application.service

import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.application.port.out.WebhookEndpointRepository
import com.pg.ochestration.application.port.out.WebhookUrlValidationResult
import com.pg.ochestration.application.port.out.WebhookUrlValidator
import com.pg.ochestration.domain.exception.WebhookEndpointDescriptionTooLongException
import com.pg.ochestration.domain.exception.WebhookEndpointLimitExceededException
import com.pg.ochestration.domain.exception.WebhookEndpointNotFoundException
import com.pg.ochestration.domain.exception.WebhookEndpointUrlNotAllowedException
import com.pg.ochestration.domain.model.Merchant
import com.pg.ochestration.domain.model.MerchantStatus
import com.pg.ochestration.domain.model.WebhookEndpoint
import com.pg.ochestration.domain.model.WebhookEndpointStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class WebhookEndpointServiceTest {

    @Test
    fun `create는 URL 검증 후 ACTIVE 엔드포인트와 signingSecret을 생성한다`() {
        val repository = FakeWebhookEndpointRepository()
        val merchantRepository = FakeMerchantRepository()
        val service = aService(merchantRepository = merchantRepository, webhookEndpointRepository = repository)

        val result = service.create(
            merchantId = "merchant-001",
            url = "https://merchant.example/webhook",
            description = "결제 결과"
        )

        assertEquals("merchant-001", result.endpoint.merchantId)
        assertEquals(WebhookEndpointStatus.ACTIVE, result.endpoint.status)
        assertEquals("결제 결과", result.endpoint.description)
        assertEquals(result.endpoint.signingSecret, result.signingSecret)
        assertNotNull(repository.findById(result.endpoint.endpointId))
        assertEquals(listOf("merchant-001"), merchantRepository.lockedMerchantIds)
    }

    @Test
    fun `create는 signingSecret을 매번 다르게 생성한다`() {
        val service = aService()

        val first = service.create("merchant-001", "https://merchant.example/first", null)
        val second = service.create("merchant-001", "https://merchant.example/second", null)

        assertNotEquals(first.signingSecret, second.signingSecret)
    }

    @Test
    fun `create는 5개 초과 등록을 거절한다`() {
        val repository = FakeWebhookEndpointRepository()
        repeat(5) { index ->
            repository.save(anEndpoint(endpointId = "endpoint-$index"))
        }
        val service = aService(webhookEndpointRepository = repository)

        assertFailsWith<WebhookEndpointLimitExceededException> {
            service.create("merchant-001", "https://merchant.example/webhook", null)
        }
    }

    @Test
    fun `create는 허용되지 않는 URL을 거절한다`() {
        val service = aService(
            merchantRepository = FakeMerchantRepository(),
            webhookEndpointRepository = FakeWebhookEndpointRepository(),
            webhookUrlValidator = BlockUrlValidator("내부망 주소")
        )

        val ex = assertFailsWith<WebhookEndpointUrlNotAllowedException> {
            service.create("merchant-001", "http://127.0.0.1/webhook", null)
        }

        assertEquals("WEBHOOK_ENDPOINT_URL_NOT_ALLOWED", ex.errorCode)
    }

    @Test
    fun `list는 해당 가맹점 엔드포인트만 반환한다`() {
        val repository = FakeWebhookEndpointRepository()
        repository.save(anEndpoint(endpointId = "endpoint-001", merchantId = "merchant-001"))
        repository.save(anEndpoint(endpointId = "endpoint-002", merchantId = "merchant-other"))
        val service = aService(webhookEndpointRepository = repository)

        val endpoints = service.list("merchant-001")

        assertEquals(listOf("endpoint-001"), endpoints.map { it.endpointId })
    }

    @Test
    fun `get은 다른 가맹점 엔드포인트를 찾을 수 없는 것으로 처리한다`() {
        val repository = FakeWebhookEndpointRepository()
        repository.save(anEndpoint(endpointId = "endpoint-001", merchantId = "merchant-other"))
        val service = aService(webhookEndpointRepository = repository)

        assertFailsWith<WebhookEndpointNotFoundException> {
            service.get("merchant-001", "endpoint-001")
        }
    }

    @Test
    fun `update는 URL 검증 후 URL 상태 설명을 변경한다`() {
        val repository = FakeWebhookEndpointRepository()
        repository.save(anEndpoint(endpointId = "endpoint-001", description = "old"))
        val service = aService(webhookEndpointRepository = repository)

        val updated = service.update(
            merchantId = "merchant-001",
            endpointId = "endpoint-001",
            url = "https://merchant.example/updated",
            status = WebhookEndpointStatus.INACTIVE,
            description = "new"
        )

        assertEquals("https://merchant.example/updated", updated.url)
        assertEquals(WebhookEndpointStatus.INACTIVE, updated.status)
        assertEquals("new", updated.description)
    }

    @Test
    fun `update는 URL과 description 필드가 없으면 기존 URL과 설명을 유지한다`() {
        val repository = FakeWebhookEndpointRepository()
        repository.save(anEndpoint(endpointId = "endpoint-001", description = "old"))
        val service = aService(webhookEndpointRepository = repository)

        val updated = service.update(
            merchantId = "merchant-001",
            endpointId = "endpoint-001",
            url = null,
            status = WebhookEndpointStatus.INACTIVE,
            description = null
        )

        assertEquals("https://merchant.example/webhook", updated.url)
        assertEquals("old", updated.description)
        assertEquals(WebhookEndpointStatus.INACTIVE, updated.status)
    }

    @Test
    fun `update는 description이 null이면 기존 설명을 유지한다`() {
        val repository = FakeWebhookEndpointRepository()
        repository.save(anEndpoint(endpointId = "endpoint-001", description = "old"))
        val service = aService(webhookEndpointRepository = repository)

        val updated = service.update(
            merchantId = "merchant-001",
            endpointId = "endpoint-001",
            url = null,
            status = null,
            description = null
        )

        assertEquals("old", updated.description)
    }

    @Test
    fun `create는 255자를 초과하는 description을 거절한다`() {
        val service = aService()

        assertFailsWith<WebhookEndpointDescriptionTooLongException> {
            service.create("merchant-001", "https://merchant.example/webhook", "a".repeat(256))
        }
    }

    @Test
    fun `deactivate는 물리 삭제하지 않고 INACTIVE로 변경한다`() {
        val repository = FakeWebhookEndpointRepository()
        repository.save(anEndpoint(endpointId = "endpoint-001"))
        val service = aService(webhookEndpointRepository = repository)

        val deactivated = service.deactivate("merchant-001", "endpoint-001")

        assertEquals(WebhookEndpointStatus.INACTIVE, deactivated.status)
        assertEquals(1, repository.countByMerchantId("merchant-001"))
    }
}

private fun aService(
    merchantRepository: MerchantRepository = FakeMerchantRepository(),
    webhookEndpointRepository: WebhookEndpointRepository = FakeWebhookEndpointRepository(),
    webhookUrlValidator: WebhookUrlValidator = AllowUrlValidator
) = WebhookEndpointService(
    merchantRepository = merchantRepository,
    webhookEndpointRepository = webhookEndpointRepository,
    webhookUrlValidator = webhookUrlValidator,
    transactionTemplate = TransactionTemplate(FakeTransactionManager)
)

private object FakeTransactionManager : PlatformTransactionManager {
    override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
        SimpleTransactionStatus()

    override fun commit(status: TransactionStatus) = Unit

    override fun rollback(status: TransactionStatus) = Unit
}

private class FakeMerchantRepository : MerchantRepository {
    val lockedMerchantIds = mutableListOf<String>()
    private val store = linkedMapOf("merchant-001" to aMerchant())

    override fun save(merchant: Merchant): Merchant {
        store[merchant.merchantId] = merchant
        return merchant
    }

    override fun findById(merchantId: String): Merchant? = store[merchantId]

    override fun findByIdForUpdate(merchantId: String): Merchant? {
        lockedMerchantIds.add(merchantId)
        return store[merchantId]
    }

    override fun findByEmail(email: String): Merchant? =
        store.values.firstOrNull { it.email == email }

    override fun existsByEmail(email: String): Boolean =
        store.values.any { it.email == email }
}

private class FakeWebhookEndpointRepository : WebhookEndpointRepository {
    private val store = linkedMapOf<String, WebhookEndpoint>()

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

private object AllowUrlValidator : WebhookUrlValidator {
    override fun validate(url: String): WebhookUrlValidationResult = WebhookUrlValidationResult.allowed()
}

private class BlockUrlValidator(
    private val reason: String
) : WebhookUrlValidator {
    override fun validate(url: String): WebhookUrlValidationResult = WebhookUrlValidationResult.blocked(reason)
}

private fun anEndpoint(
    endpointId: String,
    merchantId: String = "merchant-001",
    description: String? = null
) = WebhookEndpoint(
    endpointId = endpointId,
    merchantId = merchantId,
    url = "https://merchant.example/webhook",
    signingSecret = "secret",
    status = WebhookEndpointStatus.ACTIVE,
    description = description,
    createdAt = Instant.parse("2026-05-04T00:00:00Z"),
    updatedAt = Instant.parse("2026-05-04T00:00:00Z")
)

private fun aMerchant() = Merchant(
    merchantId = "merchant-001",
    email = "merchant@example.com",
    passwordHash = "hash",
    businessName = null,
    businessRegistrationNumber = null,
    businessRegistrationFileUrl = null,
    status = MerchantStatus.SANDBOX_ACTIVE,
    createdAt = Instant.parse("2026-05-04T00:00:00Z"),
    updatedAt = Instant.parse("2026-05-04T00:00:00Z")
)
