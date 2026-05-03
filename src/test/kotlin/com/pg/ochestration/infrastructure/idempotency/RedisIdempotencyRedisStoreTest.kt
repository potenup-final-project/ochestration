package com.pg.ochestration.infrastructure.idempotency

import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedisIdempotencyRedisStoreTest {

    private val redisTemplate: StringRedisTemplate = mock(StringRedisTemplate::class.java)
    @Suppress("UNCHECKED_CAST")
    private val valueOperations: ValueOperations<String, String> = mock(ValueOperations::class.java) as ValueOperations<String, String>
    private val dbStore: IdempotencyRecordDbStore = mock(IdempotencyRecordDbStore::class.java)
    private val objectMapper = ObjectMapper()
    private val store = RedisIdempotencyRedisStore(redisTemplate, dbStore, objectMapper)

    @Test
    fun `Redis에 COMPLETED 값이 있으면 완료 레코드를 반환한다`() {
        val redisKey = "idempotency:hash-001:idem-001"
        val cachedRecord = IdempotencyRecord(
            status = IdempotencyStatus.COMPLETED,
            httpStatus = 201,
            body = """{"paymentId":"pay-001"}"""
        )
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        `when`(valueOperations.get(redisKey)).thenReturn(objectMapper.writeValueAsString(cachedRecord))

        val result = store.find(redisKey)

        assertEquals(IdempotencyStatus.COMPLETED, result?.status)
        assertEquals(201, result?.httpStatus)
        assertEquals("""{"paymentId":"pay-001"}""", result?.body)
    }

    @Test
    fun `Redis 조회가 정상 miss이면 DB COMPLETED 폴백을 조회한다`() {
        val redisKey = "idempotency:APPROVE:merchant-001:idem-001"
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        `when`(valueOperations.get(redisKey)).thenReturn(null)
        `when`(dbStore.find(redisKey, includeProcessing = false)).thenReturn(
            IdempotencyRecord(
                status = IdempotencyStatus.COMPLETED,
                httpStatus = 200,
                body = """{"cached":true}"""
            )
        )

        val result = store.find(redisKey)

        assertEquals(IdempotencyStatus.COMPLETED, result?.status)
        assertEquals(200, result?.httpStatus)
    }

    @Test
    fun `Redis 선점 성공 시 DB에도 PROCESSING 레코드를 저장한다`() {
        val context = approveContext()
        val redisKey = context.redisKey()
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        `when`(
            valueOperations.setIfAbsent(
                eq(redisKey),
                eq("PROCESSING"),
                any(Duration::class.java) ?: Duration.ZERO
            )
        ).thenReturn(true)

        val acquired = store.tryAcquire(context)

        assertTrue(acquired)
        verify(dbStore).saveProcessingIfAbsent(context)
    }

    @Test
    fun `Redis 조회 실패 시 DB 레코드로 폴백한다`() {
        val redisKey = "idempotency:APPROVE:merchant-001:idem-001"
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        `when`(valueOperations.get(redisKey)).thenThrow(RuntimeException("redis down"))
        `when`(dbStore.find(redisKey, includeProcessing = true)).thenReturn(
            IdempotencyRecord(
                status = IdempotencyStatus.COMPLETED,
                httpStatus = 200,
                body = """{"cached":true}"""
            )
        )

        val result = store.find(redisKey)

        assertEquals(IdempotencyStatus.COMPLETED, result?.status)
        assertEquals(200, result?.httpStatus)
        assertEquals("""{"cached":true}""", result?.body)
    }

    @Test
    fun `Redis 선점 실패 시 false를 반환한다`() {
        val context = approveContext()
        val redisKey = context.redisKey()
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        `when`(
            valueOperations.setIfAbsent(
                eq(redisKey),
                eq("PROCESSING"),
                any(Duration::class.java) ?: Duration.ZERO
            )
        ).thenReturn(false)

        val acquired = store.tryAcquire(context)

        assertFalse(acquired)
    }

    private fun approveContext(): IdempotencyContext =
        IdempotencyContext(
            merchantId = "merchant-001",
            idempotencyKey = "idem-001",
            operation = IdempotencyOperation.APPROVE
        )
}
