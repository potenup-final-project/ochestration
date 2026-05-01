package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.MerchantApiKeyRepository
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.ApiKeyScope
import com.pg.ochestration.domain.model.ApiKeyStatus
import com.pg.ochestration.domain.model.MerchantApiKey
import com.pg.ochestration.infrastructure.auth.ApiKeyGenerator
import com.pg.ochestration.infrastructure.auth.ApiKeyHasher
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IssueApiKeyUseCaseTest {

    private val fakeRepository = FakeMerchantApiKeyRepository()
    private val hasher: ApiKeyHasher = mock(ApiKeyHasher::class.java)
    private val generator: ApiKeyGenerator = mock(ApiKeyGenerator::class.java)

    private val useCase = IssueApiKeyUseCase(
        merchantApiKeyRepository = fakeRepository,
        apiKeyHasher = hasher,
        apiKeyGenerator = generator,
        gracePeriodHours = 24L
    )

    // -------------------------------------------------------------------------
    // 기존 ACTIVE Key 없음 → 신규 Key 발급
    // -------------------------------------------------------------------------

    @Test
    fun `should issue new key with rawKey when no existing ACTIVE key`() {
        `when`(generator.generate(ApiKeyEnvironment.SANDBOX)).thenReturn(RAW_KEY)
        `when`(hasher.hash(RAW_KEY)).thenReturn(KEY_HASH)

        val result = useCase.issue(aCommand())

        assertEquals(RAW_KEY, result.rawKey)
        assertEquals(ApiKeyStatus.ACTIVE, result.apiKey.status)
        assertEquals("merchant-001", result.apiKey.merchantId)
        assertEquals(ApiKeyEnvironment.SANDBOX, result.apiKey.environment)
        assertEquals(KEY_HASH, result.apiKey.keyHash)
    }

    @Test
    fun `should call transitActiveToGracePeriod before saving new key`() {
        `when`(generator.generate(ApiKeyEnvironment.SANDBOX)).thenReturn(RAW_KEY)
        `when`(hasher.hash(RAW_KEY)).thenReturn(KEY_HASH)

        useCase.issue(aCommand())

        assertEquals(1, fakeRepository.transitCallCount)
    }

    // -------------------------------------------------------------------------
    // 기존 ACTIVE Key 있음 → transitActiveToGracePeriod 호출 + 신규 Key 발급
    // -------------------------------------------------------------------------

    @Test
    fun `should transit existing ACTIVE key to GRACE_PERIOD when reissuing`() {
        fakeRepository.transitReturnValue = 1
        `when`(generator.generate(ApiKeyEnvironment.SANDBOX)).thenReturn(RAW_KEY)
        `when`(hasher.hash(RAW_KEY)).thenReturn(KEY_HASH)

        val result = useCase.issue(aCommand())

        assertEquals(1, fakeRepository.transitCallCount)
        assertNotNull(result.rawKey)
        assertEquals(ApiKeyStatus.ACTIVE, result.apiKey.status)
    }

    // -------------------------------------------------------------------------
    // 발급된 Key 속성 검증
    // -------------------------------------------------------------------------

    @Test
    fun `should set keyPrefix as first 12 chars of rawKey`() {
        val rawKey = "sk_test_abcdefghijklmnopqrst"
        `when`(generator.generate(ApiKeyEnvironment.SANDBOX)).thenReturn(rawKey)
        `when`(hasher.hash(rawKey)).thenReturn(KEY_HASH)

        val result = useCase.issue(aCommand())

        assertEquals(rawKey.take(12), result.apiKey.keyPrefix)
    }

    @Test
    fun `should include PAYMENT_WRITE and PAYMENT_READ scopes in issued key`() {
        `when`(generator.generate(ApiKeyEnvironment.SANDBOX)).thenReturn(RAW_KEY)
        `when`(hasher.hash(RAW_KEY)).thenReturn(KEY_HASH)

        val result = useCase.issue(aCommand())

        assert(ApiKeyScope.PAYMENT_WRITE in result.apiKey.scopes)
        assert(ApiKeyScope.PAYMENT_READ in result.apiKey.scopes)
    }

    @Test
    fun `should set expiredAt to null for permanent key`() {
        `when`(generator.generate(ApiKeyEnvironment.SANDBOX)).thenReturn(RAW_KEY)
        `when`(hasher.hash(RAW_KEY)).thenReturn(KEY_HASH)

        val result = useCase.issue(aCommand())

        assertEquals(null, result.apiKey.expiredAt)
    }

    @Test
    fun `should set description from command when description is provided`() {
        `when`(generator.generate(ApiKeyEnvironment.SANDBOX)).thenReturn(RAW_KEY)
        `when`(hasher.hash(RAW_KEY)).thenReturn(KEY_HASH)

        val result = useCase.issue(aCommand(description = "운영 환경 Key"))

        assertEquals("운영 환경 Key", result.apiKey.description)
    }

    @Test
    fun `should set description to null when command description is null`() {
        `when`(generator.generate(ApiKeyEnvironment.SANDBOX)).thenReturn(RAW_KEY)
        `when`(hasher.hash(RAW_KEY)).thenReturn(KEY_HASH)

        val result = useCase.issue(aCommand(description = null))

        assertEquals(null, result.apiKey.description)
    }

    private companion object {
        const val RAW_KEY = "sk_test_somerawkeyfortesting"
        const val KEY_HASH = "hashed-key-value"

        fun aCommand(
            merchantId: String = "merchant-001",
            environment: ApiKeyEnvironment = ApiKeyEnvironment.SANDBOX,
            description: String? = null
        ) = IssueApiKeyCommand(
            merchantId = merchantId,
            environment = environment,
            description = description
        )
    }
}

// -------------------------------------------------------------------------
// Fake Repository
// -------------------------------------------------------------------------

private class FakeMerchantApiKeyRepository : MerchantApiKeyRepository {
    var transitReturnValue: Int = 0
    var transitCallCount: Int = 0
    private val saved = mutableListOf<MerchantApiKey>()

    override fun transitActiveToGracePeriod(
        merchantId: String,
        environment: ApiKeyEnvironment,
        graceExpiredAt: Instant
    ): Int {
        transitCallCount++
        return transitReturnValue
    }

    override fun save(apiKey: MerchantApiKey): MerchantApiKey {
        saved.add(apiKey)
        return apiKey
    }

    override fun findActiveByHash(keyHash: String): MerchantApiKey? =
        saved.firstOrNull { it.keyHash == keyHash && it.isActive() }

    override fun findAllByMerchantId(merchantId: String): List<MerchantApiKey> =
        saved.filter { it.merchantId == merchantId }

    override fun findById(keyId: String): MerchantApiKey? =
        saved.firstOrNull { it.keyId == keyId }
}
