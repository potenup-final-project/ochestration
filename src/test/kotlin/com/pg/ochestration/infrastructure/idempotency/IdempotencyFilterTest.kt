package com.pg.ochestration.infrastructure.idempotency

import com.pg.ochestration.application.port.out.MerchantApiKeyRepository
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.ApiKeyScope
import com.pg.ochestration.domain.model.ApiKeyStatus
import com.pg.ochestration.domain.model.MerchantApiKey
import com.pg.ochestration.infrastructure.auth.ApiKeyCache
import com.pg.ochestration.infrastructure.auth.ApiKeyHasher
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdempotencyFilterTest {

    private val store: IdempotencyRedisStore = mock(IdempotencyRedisStore::class.java)
    private val hasher = ApiKeyHasher(PEPPER)
    private val merchantApiKeyRepository: MerchantApiKeyRepository = mock(MerchantApiKeyRepository::class.java)
    private val apiKeyCache = ApiKeyCache(60)
    private val objectMapper = ObjectMapper()
    private val filter = IdempotencyFilter(
        idempotencyRedisStore = store,
        apiKeyHasher = hasher,
        merchantApiKeyRepository = merchantApiKeyRepository,
        apiKeyCache = apiKeyCache,
        objectMapper = objectMapper,
        authEnabled = true
    )

    // region — Idempotency-Key 헤더 없는 경우

    @Test
    fun `Idempotency-Key 헤더 없으면 Redis 체크 없이 필터 통과`() {
        val request = requestWithoutIdempotencyKey()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertTrue(chain.request != null, "필터 체인이 실행되어야 한다")
        verify(store, never()).find(org.mockito.ArgumentMatchers.anyString())
    }

    // endregion

    // region — X-Api-Key 헤더 없는 경우

    @Test
    fun `Idempotency-Key는 있지만 X-Api-Key 헤더 없으면 필터 통과`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Idempotency-Key", IDEMPOTENCY_KEY)
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertTrue(chain.request != null, "필터 체인이 실행되어야 한다")
        verify(store, never()).find(org.mockito.ArgumentMatchers.anyString())
    }

    // endregion

    // region — 신규 요청 (Redis에 키 없음)

    @Test
    fun `신규 요청이면 PROCESSING으로 선점 후 필터 체인 통과`() {
        val redisKey = expectedRedisKey()
        val context = approveContext()
        `when`(store.find(redisKey)).thenReturn(null)
        `when`(store.tryAcquire(context)).thenReturn(true)

        val request = fullRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertTrue(chain.request != null, "필터 체인이 실행되어야 한다")
        verify(store).tryAcquire(context)
    }

    @Test
    fun `신규 요청 처리 완료 후 COMPLETED 상태로 Redis에 저장`() {
        val redisKey = expectedRedisKey()
        val context = approveContext()
        `when`(store.find(redisKey)).thenReturn(null)
        `when`(store.tryAcquire(context)).thenReturn(true)

        val request = fullRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        verify(store).complete(
            org.mockito.ArgumentMatchers.eq(context) ?: context,
            org.mockito.ArgumentMatchers.eq(HttpStatus.OK.value()),
            org.mockito.ArgumentMatchers.any(String::class.java) ?: ""
        )
    }

    // endregion

    // region — COMPLETED 상태 (캐시 히트)

    @Test
    fun `COMPLETED 캐시 히트이면 필터 체인 건너뛰고 캐시된 응답 반환`() {
        val redisKey = expectedRedisKey()
        val cachedBody = """{"paymentId":"pay-001","status":"APPROVED"}"""
        `when`(store.find(redisKey)).thenReturn(
            IdempotencyRecord(
                status = IdempotencyStatus.COMPLETED,
                httpStatus = HttpStatus.OK.value(),
                body = cachedBody
            )
        )

        val request = fullRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertFalse(chain.request != null, "필터 체인이 실행되면 안 된다")
        assertEquals(HttpStatus.OK.value(), response.status)
        assertEquals(cachedBody, response.contentAsString)
    }

    @Test
    fun `COMPLETED 캐시 히트 시 원래 HTTP 상태 코드 그대로 반환`() {
        val redisKey = expectedRedisKey()
        `when`(store.find(redisKey)).thenReturn(
            IdempotencyRecord(
                status = IdempotencyStatus.COMPLETED,
                httpStatus = HttpStatus.BAD_REQUEST.value(),
                body = """{"errorCode":"INVALID_REQUEST"}"""
            )
        )

        val request = fullRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.status)
    }

    // endregion

    // region — PROCESSING 상태 (중복 요청)

    @Test
    fun `PROCESSING 상태이면 필터 체인 건너뛰고 409 반환`() {
        val redisKey = expectedRedisKey()
        `when`(store.find(redisKey)).thenReturn(
            IdempotencyRecord(status = IdempotencyStatus.PROCESSING)
        )

        val request = fullRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertFalse(chain.request != null, "필터 체인이 실행되면 안 된다")
        assertEquals(HttpStatus.CONFLICT.value(), response.status)
    }

    @Test
    fun `PROCESSING 409 응답 바디에 IDEMPOTENCY_PROCESSING errorCode 포함`() {
        val redisKey = expectedRedisKey()
        `when`(store.find(redisKey)).thenReturn(
            IdempotencyRecord(status = IdempotencyStatus.PROCESSING)
        )

        val request = fullRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertTrue(response.contentAsString.contains("IDEMPOTENCY_PROCESSING"))
    }

    // endregion

    // region — tryAcquire 경합 (find 후 setIfAbsent 실패)

    @Test
    fun `find는 null이지만 tryAcquire 실패 시 409 반환`() {
        val redisKey = expectedRedisKey()
        val context = approveContext()
        `when`(store.find(redisKey)).thenReturn(null)
        `when`(store.tryAcquire(context)).thenReturn(false)

        val request = fullRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertFalse(chain.request != null, "필터 체인이 실행되면 안 된다")
        assertEquals(HttpStatus.CONFLICT.value(), response.status)
    }

    // endregion

    @Test
    fun `유효하지 않은 API Key는 멱등성 저장소를 호출하지 않고 필터 통과`() {
        val request = paymentPostRequest().apply {
            addHeader("Idempotency-Key", IDEMPOTENCY_KEY)
            addHeader("X-Api-Key", "sk_test_invalid")
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertTrue(chain.request != null, "필터 체인이 실행되어야 한다")
        verify(store, never()).find(org.mockito.ArgumentMatchers.anyString())
    }

    @Test
    fun `cancel 요청은 CANCEL operation 키를 사용한다`() {
        val keyHash = hasher.hash(RAW_API_KEY)
        val context = cancelContext()
        val redisKey = "idempotency:CANCEL:$MERCHANT_ID:$IDEMPOTENCY_KEY"
        `when`(merchantApiKeyRepository.findActiveByHash(keyHash)).thenReturn(activeApiKey(keyHash))
        `when`(store.buildRedisKey(context)).thenReturn(redisKey)
        `when`(store.find(redisKey)).thenReturn(null)
        `when`(store.tryAcquire(context)).thenReturn(true)
        val request = MockHttpServletRequest("POST", "/api/payments/pay-001/cancel").apply {
            addHeader("Idempotency-Key", IDEMPOTENCY_KEY)
            addHeader("X-Api-Key", RAW_API_KEY)
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        verify(store).buildRedisKey(context)
        verify(store).tryAcquire(context)
    }

    // region — 헬퍼

    private fun fullRequest(): MockHttpServletRequest =
        paymentPostRequest().apply {
            addHeader("Idempotency-Key", IDEMPOTENCY_KEY)
            addHeader("X-Api-Key", RAW_API_KEY)
        }

    private fun requestWithoutIdempotencyKey(): MockHttpServletRequest =
        paymentPostRequest().apply {
            addHeader("X-Api-Key", RAW_API_KEY)
        }

    private fun expectedRedisKey(): String {
        val keyHash = hasher.hash(RAW_API_KEY)
        val context = approveContext()
        val key = "idempotency:APPROVE:$MERCHANT_ID:$IDEMPOTENCY_KEY"
        `when`(merchantApiKeyRepository.findActiveByHash(keyHash)).thenReturn(activeApiKey(keyHash))
        `when`(store.buildRedisKey(context)).thenReturn(key)
        return key
    }

    private fun approveContext(): IdempotencyContext =
        IdempotencyContext(
            merchantId = MERCHANT_ID,
            idempotencyKey = IDEMPOTENCY_KEY,
            operation = IdempotencyOperation.APPROVE
        )

    private fun cancelContext(): IdempotencyContext =
        IdempotencyContext(
            merchantId = MERCHANT_ID,
            idempotencyKey = IDEMPOTENCY_KEY,
            operation = IdempotencyOperation.CANCEL
        )

    private fun paymentPostRequest(): MockHttpServletRequest =
        MockHttpServletRequest("POST", "/api/payments/approve")

    private fun activeApiKey(keyHash: String): MerchantApiKey =
        MerchantApiKey(
            keyId = "key-001",
            merchantId = MERCHANT_ID,
            keyHash = keyHash,
            keyPrefix = "sk_test",
            environment = ApiKeyEnvironment.SANDBOX,
            status = ApiKeyStatus.ACTIVE,
            scopes = setOf(ApiKeyScope.PAYMENT_WRITE, ApiKeyScope.PAYMENT_READ),
            description = "test",
            expiredAt = null,
            graceExpiredAt = null,
            revokedAt = null,
            createdAt = Instant.now(),
            lastUsedAt = null
        )

    private companion object {
        const val PEPPER = "test-pepper-value-that-is-32chars"
        const val MERCHANT_ID = "merchant-001"
        const val RAW_API_KEY = "sk_test_somerawkeyfortesting"
        const val IDEMPOTENCY_KEY = "order-20260501-001"
    }

    // endregion
}
