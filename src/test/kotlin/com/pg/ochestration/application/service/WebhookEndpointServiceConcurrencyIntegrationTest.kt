package com.pg.ochestration.application.service

import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.application.port.out.WebhookEndpointRepository
import com.pg.ochestration.application.port.out.WebhookUrlValidationResult
import com.pg.ochestration.application.port.out.WebhookUrlValidator
import com.pg.ochestration.domain.exception.WebhookEndpointLimitExceededException
import com.pg.ochestration.domain.model.Merchant
import com.pg.ochestration.domain.model.MerchantStatus
import com.pg.ochestration.domain.model.WebhookEndpoint
import com.pg.ochestration.domain.model.WebhookEndpointStatus
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:webhook-concurrency;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "server.port=0",
        "gateway.toss-test.base-url=http://localhost",
        "gateway.toss-test.secret-key=test-secret",
        "gateway.toss-test.connect-timeout-ms=1000",
        "gateway.toss-test.read-timeout-ms=1000",
        "auth.api-key.pepper=test-pepper-that-is-32-characters-minimum",
        "auth.api-key.enabled=false"
    ]
)
class WebhookEndpointServiceConcurrencyIntegrationTest {

    @Autowired lateinit var service: WebhookEndpointService
    @Autowired lateinit var merchantRepository: MerchantRepository
    @Autowired lateinit var webhookEndpointRepository: WebhookEndpointRepository

    @Test
    fun `동시에 엔드포인트를 생성해도 가맹점당 최대 5개 제한을 지킨다`() {
        val merchantId = UUID.randomUUID().toString()
        merchantRepository.save(aMerchant(merchantId))
        repeat(4) { index ->
            webhookEndpointRepository.save(anEndpoint(endpointId = UUID.randomUUID().toString(), merchantId = merchantId))
        }

        val threadCount = 8
        val executor = Executors.newFixedThreadPool(threadCount)
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<Result<WebhookEndpointCreateResult>>())

        repeat(threadCount) { index ->
            executor.submit {
                readyLatch.countDown()
                startLatch.await(5, TimeUnit.SECONDS)
                results += runCatching {
                    service.create(
                        merchantId = merchantId,
                        url = "https://merchant.example/webhook-$index",
                        description = null
                    )
                }
            }
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
        startLatch.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

        val successes = results.count { it.isSuccess }
        val limitFailures = results.mapNotNull { it.exceptionOrNull() }
            .filterIsInstance<WebhookEndpointLimitExceededException>()

        assertEquals(1, successes)
        assertEquals(threadCount - 1, limitFailures.size)
        assertEquals(5, webhookEndpointRepository.countByMerchantId(merchantId))
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun webhookUrlValidator(): WebhookUrlValidator = object : WebhookUrlValidator {
            override fun validate(url: String): WebhookUrlValidationResult =
                WebhookUrlValidationResult.allowed()
        }
    }
}

private fun aMerchant(merchantId: String) = Merchant(
    merchantId = merchantId,
    email = "$merchantId@example.com",
    passwordHash = "hash",
    businessName = null,
    businessRegistrationNumber = null,
    businessRegistrationFileUrl = null,
    status = MerchantStatus.SANDBOX_ACTIVE,
    createdAt = Instant.parse("2026-05-04T00:00:00Z"),
    updatedAt = Instant.parse("2026-05-04T00:00:00Z")
)

private fun anEndpoint(endpointId: String, merchantId: String) = WebhookEndpoint(
    endpointId = endpointId,
    merchantId = merchantId,
    url = "https://merchant.example/webhook",
    signingSecret = "secret",
    status = WebhookEndpointStatus.ACTIVE,
    description = null,
    createdAt = Instant.parse("2026-05-04T00:00:00Z"),
    updatedAt = Instant.parse("2026-05-04T00:00:00Z")
)
