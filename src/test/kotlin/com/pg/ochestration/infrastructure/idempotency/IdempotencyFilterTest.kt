package com.pg.ochestration.infrastructure.idempotency

import com.pg.ochestration.infrastructure.auth.ApiKeyHasher
import tools.jackson.databind.ObjectMapper
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdempotencyFilterTest {

    private val store: IdempotencyRedisStore = mock(IdempotencyRedisStore::class.java)
    private val hasher = ApiKeyHasher(PEPPER)
    private val objectMapper = ObjectMapper()
    private val filter = IdempotencyFilter(store, hasher, objectMapper)

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
        `when`(store.find(redisKey)).thenReturn(null)
        `when`(store.tryAcquire(redisKey)).thenReturn(true)

        val request = fullRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertTrue(chain.request != null, "필터 체인이 실행되어야 한다")
        verify(store).tryAcquire(redisKey)
    }

    @Test
    fun `신규 요청 처리 완료 후 COMPLETED 상태로 Redis에 저장`() {
        val redisKey = expectedRedisKey()
        `when`(store.find(redisKey)).thenReturn(null)
        `when`(store.tryAcquire(redisKey)).thenReturn(true)

        val request = fullRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        verify(store).complete(
            org.mockito.ArgumentMatchers.eq(redisKey) ?: redisKey,
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
        `when`(store.find(redisKey)).thenReturn(null)
        `when`(store.tryAcquire(redisKey)).thenReturn(false)

        val request = fullRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertFalse(chain.request != null, "필터 체인이 실행되면 안 된다")
        assertEquals(HttpStatus.CONFLICT.value(), response.status)
    }

    // endregion

    // region — 헬퍼

    private fun fullRequest(): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            addHeader("Idempotency-Key", IDEMPOTENCY_KEY)
            addHeader("X-Api-Key", RAW_API_KEY)
        }

    private fun requestWithoutIdempotencyKey(): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            addHeader("X-Api-Key", RAW_API_KEY)
        }

    private fun expectedRedisKey(): String {
        val apiKeyHash = hasher.hash(RAW_API_KEY)
        val key = "idempotency:$apiKeyHash:$IDEMPOTENCY_KEY"
        `when`(store.buildRedisKey(apiKeyHash, IDEMPOTENCY_KEY)).thenReturn(key)
        return key
    }

    private companion object {
        const val PEPPER = "test-pepper-value-that-is-32chars"
        const val RAW_API_KEY = "sk_test_somerawkeyfortesting"
        const val IDEMPOTENCY_KEY = "order-20260501-001"
    }

    // endregion
}
