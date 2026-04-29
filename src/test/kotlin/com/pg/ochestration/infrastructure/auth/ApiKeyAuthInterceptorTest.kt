package com.pg.ochestration.infrastructure.auth

import com.pg.ochestration.application.port.out.MerchantApiKeyRepository
import com.pg.ochestration.domain.exception.ExpiredApiKeyException
import com.pg.ochestration.domain.exception.InvalidApiKeyException
import com.pg.ochestration.domain.exception.MissingApiKeyException
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.ApiKeyScope
import com.pg.ochestration.domain.model.ApiKeyStatus
import com.pg.ochestration.domain.model.MerchantApiKey
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiKeyAuthInterceptorTest {

    private val repository: MerchantApiKeyRepository = mock(MerchantApiKeyRepository::class.java)
    private val hasher = ApiKeyHasher(PEPPER)
    private val cache = ApiKeyCache(ttlSeconds = 60L)

    private val enabledInterceptor = ApiKeyAuthInterceptor(
        merchantApiKeyRepository = repository,
        apiKeyHasher = hasher,
        apiKeyCache = cache,
        enabled = true
    )

    private val disabledInterceptor = ApiKeyAuthInterceptor(
        merchantApiKeyRepository = repository,
        apiKeyHasher = hasher,
        apiKeyCache = cache,
        enabled = false
    )

    @Test
    fun `should return true without setting attribute when auth is disabled`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        val result = disabledInterceptor.preHandle(request, response, Any())

        assertTrue(result)
        assertNull(request.getAttribute(MerchantContext.ATTR_KEY))
    }

    @Test
    fun `should throw MissingApiKeyException when X-Api-Key header is absent`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        assertFailsWith<MissingApiKeyException> {
            enabledInterceptor.preHandle(request, response, Any())
        }
    }

    @Test
    fun `should throw InvalidApiKeyException when key hash is not found in repository`() {
        val request = requestWith(RAW_KEY)
        val response = MockHttpServletResponse()
        `when`(repository.findActiveByHash(hasher.hash(RAW_KEY))).thenReturn(null)

        assertFailsWith<InvalidApiKeyException> {
            enabledInterceptor.preHandle(request, response, Any())
        }
    }

    @Test
    fun `should throw InvalidApiKeyException when repository returns null for REVOKED key`() {
        val request = requestWith(RAW_KEY)
        val response = MockHttpServletResponse()
        `when`(repository.findActiveByHash(hasher.hash(RAW_KEY))).thenReturn(null)

        assertFailsWith<InvalidApiKeyException> {
            enabledInterceptor.preHandle(request, response, Any())
        }
    }

    @Test
    fun `should return true and set MerchantPrincipal attribute when key is valid ACTIVE`() {
        val activeKey = anApiKey(status = ApiKeyStatus.ACTIVE)
        val request = requestWith(RAW_KEY)
        val response = MockHttpServletResponse()
        `when`(repository.findActiveByHash(hasher.hash(RAW_KEY))).thenReturn(activeKey)

        val result = enabledInterceptor.preHandle(request, response, Any())

        assertTrue(result)
        val principal = request.getAttribute(MerchantContext.ATTR_KEY) as MerchantPrincipal
        assertEquals("merchant-001", principal.merchantId)
        assertEquals(ApiKeyEnvironment.SANDBOX, principal.environment)
    }

    @Test
    fun `should throw ExpiredApiKeyException when GRACE_PERIOD key has expired graceExpiredAt`() {
        val expiredGraceKey = anApiKey(
            status = ApiKeyStatus.GRACE_PERIOD,
            graceExpiredAt = Instant.now().minusSeconds(1)
        )
        val request = requestWith(RAW_KEY)
        val response = MockHttpServletResponse()
        `when`(repository.findActiveByHash(hasher.hash(RAW_KEY))).thenReturn(expiredGraceKey)

        assertFailsWith<ExpiredApiKeyException> {
            enabledInterceptor.preHandle(request, response, Any())
        }
    }

    @Test
    fun `should return true when GRACE_PERIOD key is still valid`() {
        val validGraceKey = anApiKey(
            status = ApiKeyStatus.GRACE_PERIOD,
            graceExpiredAt = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        val request = requestWith(RAW_KEY)
        val response = MockHttpServletResponse()
        `when`(repository.findActiveByHash(hasher.hash(RAW_KEY))).thenReturn(validGraceKey)

        val result = enabledInterceptor.preHandle(request, response, Any())

        assertTrue(result)
        val principal = request.getAttribute(MerchantContext.ATTR_KEY) as MerchantPrincipal
        assertEquals("merchant-001", principal.merchantId)
    }

    @Test
    fun `should return true without calling repository when principal is found in cache`() {
        val principal = MerchantPrincipal(merchantId = "merchant-001", environment = ApiKeyEnvironment.SANDBOX)
        val keyHash = hasher.hash(RAW_KEY)
        cache.put(keyHash, principal)

        val request = requestWith(RAW_KEY)
        val response = MockHttpServletResponse()

        val result = enabledInterceptor.preHandle(request, response, Any())

        assertTrue(result)
        assertEquals(principal, request.getAttribute(MerchantContext.ATTR_KEY) as MerchantPrincipal)
        verify(repository, never()).findActiveByHash(keyHash)
    }

    private fun requestWith(rawKey: String): MockHttpServletRequest =
        MockHttpServletRequest().apply { addHeader("X-Api-Key", rawKey) }

    private companion object {
        const val PEPPER = "test-pepper-value-that-is-32chars"
        const val RAW_KEY = "sk_test_somerawkeyfortesting"

        fun anApiKey(
            keyId: String = "key-001",
            merchantId: String = "merchant-001",
            status: ApiKeyStatus = ApiKeyStatus.ACTIVE,
            environment: ApiKeyEnvironment = ApiKeyEnvironment.SANDBOX,
            expiredAt: Instant? = null,
            graceExpiredAt: Instant? = null,
        ) = MerchantApiKey(
            keyId = keyId,
            merchantId = merchantId,
            keyHash = "irrelevant-stored-hash",
            keyPrefix = "sk_test_ab12",
            environment = environment,
            status = status,
            scopes = setOf(ApiKeyScope.PAYMENT_WRITE),
            description = null,
            expiredAt = expiredAt,
            graceExpiredAt = graceExpiredAt,
            revokedAt = null,
            createdAt = Instant.now(),
            lastUsedAt = null
        )
    }
}
