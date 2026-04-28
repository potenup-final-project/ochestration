package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.ApiKeyScope
import com.pg.ochestration.domain.model.ApiKeyStatus
import com.pg.ochestration.infrastructure.persistence.jpa.entity.MerchantApiKeyJpaEntity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DataJpaTest
@ActiveProfiles("test")
class MerchantApiKeyJpaRepositoryTest {

    @Autowired lateinit var repository: MerchantApiKeyJpaRepository
    @Autowired lateinit var em: TestEntityManager

    // findByKeyHashAndStatusIn 테스트

    @Test
    fun `should return entity when ACTIVE key is queried with ACTIVE status`() {
        val entity = anEntity(status = ApiKeyStatus.ACTIVE)
        em.persistAndFlush(entity)

        val found = repository.findByKeyHashAndStatusIn(entity.keyHash, listOf(ApiKeyStatus.ACTIVE))

        assertNotNull(found)
        assertEquals(entity.keyId, found.keyId)
    }

    @Test
    fun `should return entity when GRACE_PERIOD key is queried with ACTIVE and GRACE_PERIOD statuses`() {
        val entity = anEntity(
            status = ApiKeyStatus.GRACE_PERIOD,
            graceExpiredAt = Instant.now().plusSeconds(3600)
        )
        em.persistAndFlush(entity)

        val found = repository.findByKeyHashAndStatusIn(
            entity.keyHash,
            listOf(ApiKeyStatus.ACTIVE, ApiKeyStatus.GRACE_PERIOD)
        )

        assertNotNull(found)
        assertEquals(entity.keyId, found.keyId)
    }

    @Test
    fun `should return null when REVOKED key is queried with only ACTIVE status`() {
        val entity = anEntity(status = ApiKeyStatus.REVOKED)
        em.persistAndFlush(entity)

        val found = repository.findByKeyHashAndStatusIn(entity.keyHash, listOf(ApiKeyStatus.ACTIVE))

        assertNull(found)
    }

    @Test
    fun `should return null when queried with non-existent hash`() {
        val found = repository.findByKeyHashAndStatusIn("non-existent-hash", listOf(ApiKeyStatus.ACTIVE))

        assertNull(found)
    }

    // transitActiveToGracePeriod 테스트

    @Test
    fun `should update status to GRACE_PERIOD and return 1 when ACTIVE key exists`() {
        val entity = anEntity(merchantId = "merchant-grace-001", status = ApiKeyStatus.ACTIVE)
        em.persistAndFlush(entity)
        em.clear()

        val graceExpiredAt = Instant.now().plusSeconds(86400)
        val updatedCount = repository.transitActiveToGracePeriod(
            merchantId = "merchant-grace-001",
            environment = ApiKeyEnvironment.SANDBOX,
            graceExpiredAt = graceExpiredAt
        )
        em.flush()
        em.clear()

        assertEquals(1, updatedCount)
        val reloaded = repository.findById(entity.keyId).orElseThrow()
        assertEquals(ApiKeyStatus.GRACE_PERIOD, reloaded.status)
        assertNotNull(reloaded.graceExpiredAt)
    }

    @Test
    fun `should return 0 when no ACTIVE key exists for given merchantId`() {
        val updatedCount = repository.transitActiveToGracePeriod(
            merchantId = "non-existent-merchant",
            environment = ApiKeyEnvironment.SANDBOX,
            graceExpiredAt = Instant.now().plusSeconds(86400)
        )

        assertEquals(0, updatedCount)
    }

    @Test
    fun `should not affect GRACE_PERIOD key when transitActiveToGracePeriod is called`() {
        val entity = anEntity(
            merchantId = "merchant-grace-002",
            status = ApiKeyStatus.GRACE_PERIOD,
            graceExpiredAt = Instant.now().plusSeconds(3600)
        )
        em.persistAndFlush(entity)

        val updatedCount = repository.transitActiveToGracePeriod(
            merchantId = "merchant-grace-002",
            environment = ApiKeyEnvironment.SANDBOX,
            graceExpiredAt = Instant.now().plusSeconds(86400)
        )

        assertEquals(0, updatedCount)
    }

    // findAllByMerchantId 테스트

    @Test
    fun `should return all keys for the same merchantId`() {
        val merchantId = "merchant-findall-001"
        val first = anEntity(keyId = UUID.randomUUID().toString(), merchantId = merchantId, keyHash = "hash-first-001")
        val second = anEntity(keyId = UUID.randomUUID().toString(), merchantId = merchantId, keyHash = "hash-second-001")
        em.persistAndFlush(first)
        em.persistAndFlush(second)

        val result = repository.findAllByMerchantId(merchantId)

        assertEquals(2, result.size)
        assert(result.all { it.merchantId == merchantId })
    }

    @Test
    fun `should not include keys from different merchantId`() {
        val targetMerchantId = "merchant-findall-002"
        val otherMerchantId = "merchant-findall-other"
        val targetKey = anEntity(keyId = UUID.randomUUID().toString(), merchantId = targetMerchantId, keyHash = "hash-target-002")
        val otherKey = anEntity(keyId = UUID.randomUUID().toString(), merchantId = otherMerchantId, keyHash = "hash-other-002")
        em.persistAndFlush(targetKey)
        em.persistAndFlush(otherKey)

        val result = repository.findAllByMerchantId(targetMerchantId)

        assertEquals(1, result.size)
        assertEquals(targetMerchantId, result.first().merchantId)
    }
}

// 픽스처 헬퍼
private fun anEntity(
    keyId: String = UUID.randomUUID().toString(),
    merchantId: String = "merchant-001",
    keyHash: String = "hash-$keyId",
    status: ApiKeyStatus = ApiKeyStatus.ACTIVE,
    environment: ApiKeyEnvironment = ApiKeyEnvironment.SANDBOX,
    graceExpiredAt: Instant? = null,
) = MerchantApiKeyJpaEntity(
    keyId = keyId,
    merchantId = merchantId,
    keyHash = keyHash,
    keyPrefix = "sk_test_ab12",
    environment = environment,
    status = status,
    scopes = setOf(ApiKeyScope.PAYMENT_READ),
    description = null,
    expiredAt = null,
    graceExpiredAt = graceExpiredAt,
    revokedAt = null,
    createdAt = Instant.now(),
    lastUsedAt = null
)
