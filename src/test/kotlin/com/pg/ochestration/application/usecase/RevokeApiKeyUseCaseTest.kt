package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.ApiKeyCachePort
import com.pg.ochestration.application.port.out.MerchantApiKeyRepository
import com.pg.ochestration.domain.exception.InvalidApiKeyException
import com.pg.ochestration.domain.exception.InvalidApiKeyStateException
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.ApiKeyScope
import com.pg.ochestration.domain.model.ApiKeyStatus
import com.pg.ochestration.domain.model.MerchantApiKey
import com.pg.ochestration.infrastructure.auth.ApiKeyHasher
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RevokeApiKeyUseCaseTest {

    private val fakeRepository = FakeRevokeRepository()
    private val hasher: ApiKeyHasher = mock(ApiKeyHasher::class.java)
    private val cachePort: ApiKeyCachePort = mock(ApiKeyCachePort::class.java)

    private val useCase = RevokeApiKeyUseCase(
        merchantApiKeyRepository = fakeRepository,
        apiKeyHasher = hasher,
        apiKeyCachePort = cachePort
    )

    // -------------------------------------------------------------------------
    // 정상 폐기 (happy path)
    // -------------------------------------------------------------------------

    @Test
    fun `should return REVOKED key when valid rawKey and matching keyId are provided`() {
        val key = anApiKey(keyId = "key-001", status = ApiKeyStatus.ACTIVE)
        fakeRepository.activeByHash[KEY_HASH] = key
        `when`(hasher.hash(RAW_KEY)).thenReturn(KEY_HASH)

        val result = useCase.revoke(keyId = "key-001", rawKey = RAW_KEY)

        assertEquals(ApiKeyStatus.REVOKED, result.status)
    }

    @Test
    fun `should call cachePort evict after saving when revoke succeeds`() {
        val key = anApiKey(keyId = "key-001", status = ApiKeyStatus.ACTIVE)
        fakeRepository.activeByHash[KEY_HASH] = key
        `when`(hasher.hash(RAW_KEY)).thenReturn(KEY_HASH)

        useCase.revoke(keyId = "key-001", rawKey = RAW_KEY)

        verify(cachePort).evict(KEY_HASH)
    }

    @Test
    fun `should return GRACE_PERIOD key as REVOKED when revoking valid GRACE_PERIOD key`() {
        val graceKey = anApiKey(
            keyId = "key-grace",
            status = ApiKeyStatus.GRACE_PERIOD,
            graceExpiredAt = Instant.now().plusSeconds(3600)
        )
        fakeRepository.activeByHash[KEY_HASH] = graceKey
        `when`(hasher.hash(RAW_KEY)).thenReturn(KEY_HASH)

        val result = useCase.revoke(keyId = "key-grace", rawKey = RAW_KEY)

        assertEquals(ApiKeyStatus.REVOKED, result.status)
    }

    // -------------------------------------------------------------------------
    // 잘못된 rawKey (hash 불일치)
    // -------------------------------------------------------------------------

    @Test
    fun `should throw InvalidApiKeyException when rawKey hash does not match any active key`() {
        `when`(hasher.hash(INVALID_RAW_KEY)).thenReturn("unknown-hash")

        assertFailsWith<InvalidApiKeyException> {
            useCase.revoke(keyId = "key-001", rawKey = INVALID_RAW_KEY)
        }
    }

    @Test
    fun `should not call cachePort evict when rawKey hash matches no active key`() {
        `when`(hasher.hash(INVALID_RAW_KEY)).thenReturn("unknown-hash")

        runCatching { useCase.revoke(keyId = "key-001", rawKey = INVALID_RAW_KEY) }

        verify(cachePort, never()).evict("unknown-hash")
    }

    // -------------------------------------------------------------------------
    // keyId 불일치
    // -------------------------------------------------------------------------

    @Test
    fun `should throw InvalidApiKeyException when keyId does not match found key`() {
        val key = anApiKey(keyId = "key-001", status = ApiKeyStatus.ACTIVE)
        fakeRepository.activeByHash[KEY_HASH] = key
        `when`(hasher.hash(RAW_KEY)).thenReturn(KEY_HASH)

        assertFailsWith<InvalidApiKeyException> {
            useCase.revoke(keyId = "key-different", rawKey = RAW_KEY)
        }
    }

    // -------------------------------------------------------------------------
    // 이미 REVOKED 상태 → InvalidApiKeyStateException (도메인 revoke()에서 발생)
    // -------------------------------------------------------------------------

    @Test
    fun `should throw InvalidApiKeyStateException when key is already REVOKED`() {
        val revokedKey = anApiKey(
            keyId = "key-001",
            status = ApiKeyStatus.REVOKED,
            revokedAt = Instant.now().minusSeconds(60)
        )
        fakeRepository.activeByHash[KEY_HASH] = revokedKey
        `when`(hasher.hash(RAW_KEY)).thenReturn(KEY_HASH)

        assertFailsWith<InvalidApiKeyStateException> {
            useCase.revoke(keyId = "key-001", rawKey = RAW_KEY)
        }
    }

    // -------------------------------------------------------------------------
    // evict 실패 → 예외 삼키고 정상 반환
    // -------------------------------------------------------------------------

    @Test
    fun `should return saved key without throwing when cachePort evict throws exception`() {
        val key = anApiKey(keyId = "key-001", status = ApiKeyStatus.ACTIVE)
        fakeRepository.activeByHash[KEY_HASH] = key
        `when`(hasher.hash(RAW_KEY)).thenReturn(KEY_HASH)
        doThrow(RuntimeException("캐시 서버 연결 실패")).`when`(cachePort).evict(KEY_HASH)

        val result = useCase.revoke(keyId = "key-001", rawKey = RAW_KEY)

        assertEquals(ApiKeyStatus.REVOKED, result.status)
    }

    // -------------------------------------------------------------------------
    // Fixture
    // -------------------------------------------------------------------------

    private companion object {
        const val RAW_KEY = "sk_test_somerawkeyfortesting"
        const val INVALID_RAW_KEY = "sk_test_invalidkeyvalue"
        const val KEY_HASH = "hashed-key-value"

        fun anApiKey(
            keyId: String = "key-001",
            merchantId: String = "merchant-001",
            status: ApiKeyStatus = ApiKeyStatus.ACTIVE,
            environment: ApiKeyEnvironment = ApiKeyEnvironment.SANDBOX,
            graceExpiredAt: Instant? = null,
            revokedAt: Instant? = null,
        ) = MerchantApiKey(
            keyId = keyId,
            merchantId = merchantId,
            keyHash = KEY_HASH,
            keyPrefix = "sk_test_ab12",
            environment = environment,
            status = status,
            scopes = setOf(ApiKeyScope.PAYMENT_WRITE, ApiKeyScope.PAYMENT_READ),
            description = null,
            expiredAt = null,
            graceExpiredAt = graceExpiredAt,
            revokedAt = revokedAt,
            createdAt = Instant.now(),
            lastUsedAt = null
        )
    }
}

// -------------------------------------------------------------------------
// Fake Repository
// -------------------------------------------------------------------------

private class FakeRevokeRepository : MerchantApiKeyRepository {
    val activeByHash = mutableMapOf<String, MerchantApiKey>()
    private val saved = mutableListOf<MerchantApiKey>()

    override fun findActiveByHash(keyHash: String): MerchantApiKey? = activeByHash[keyHash]

    override fun save(apiKey: MerchantApiKey): MerchantApiKey {
        saved.add(apiKey)
        return apiKey
    }

    override fun findAllByMerchantId(merchantId: String): List<MerchantApiKey> =
        saved.filter { it.merchantId == merchantId }

    override fun findById(keyId: String): MerchantApiKey? =
        saved.firstOrNull { it.keyId == keyId }

    override fun transitActiveToGracePeriod(
        merchantId: String,
        environment: ApiKeyEnvironment,
        graceExpiredAt: Instant
    ): Int = 0
}
