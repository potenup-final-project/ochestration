package com.pg.ochestration.infrastructure.idempotency

import com.pg.ochestration.infrastructure.idempotency.IdempotencyStatus.COMPLETED
import com.pg.ochestration.infrastructure.idempotency.IdempotencyStatus.PROCESSING
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Component
class RedisIdempotencyRedisStore(
    private val redisTemplate: StringRedisTemplate,
    private val dbStore: IdempotencyRecordDbStore,
    private val objectMapper: ObjectMapper
) : IdempotencyRedisStore {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun buildRedisKey(context: IdempotencyContext): String = context.redisKey()

    override fun find(redisKey: String): IdempotencyRecord? {
        var redisFailed = false
        val redisRecord = runCatching {
            redisTemplate.opsForValue().get(redisKey)?.let(::deserialize)
        }.onFailure { ex ->
            redisFailed = true
            log.warn("Redis 멱등성 조회 실패, DB 폴백을 사용합니다: redisKey={}, error={}", redisKey, ex.message)
        }.getOrNull()

        return redisRecord ?: dbStore.find(redisKey, includeProcessing = redisFailed)
    }

    override fun tryAcquire(context: IdempotencyContext): Boolean {
        val redisKey = buildRedisKey(context)
        val acquired = runCatching {
            redisTemplate.opsForValue()
                .setIfAbsent(redisKey, PROCESSING_VALUE, PROCESSING_TTL)
        }.onFailure { ex ->
            log.warn("Redis 멱등성 선점 실패, DB 폴백을 사용합니다: redisKey={}, error={}", redisKey, ex.message)
        }.getOrNull()

        return if (acquired == true) {
            dbStore.saveProcessingIfAbsent(context)
            true
        } else if (acquired == false) {
            false
        } else {
            dbStore.acquire(context)
        }
    }

    override fun complete(context: IdempotencyContext, httpStatus: Int, body: String) {
        val redisKey = buildRedisKey(context)
        val record = IdempotencyRecord(
            status = COMPLETED,
            httpStatus = httpStatus,
            body = body
        )

        runCatching {
            redisTemplate.opsForValue()
                .set(redisKey, objectMapper.writeValueAsString(record), COMPLETED_TTL)
        }.onFailure { ex ->
            log.warn("Redis 멱등성 완료 저장 실패, DB에는 저장을 시도합니다: redisKey={}, error={}", redisKey, ex.message)
        }

        dbStore.complete(context, httpStatus, body)
    }

    private fun deserialize(value: String): IdempotencyRecord =
        if (value == PROCESSING_VALUE) {
            IdempotencyRecord(status = PROCESSING)
        } else {
            objectMapper.readValue(value, IdempotencyRecord::class.java)
        }

    private companion object {
        const val PROCESSING_VALUE = "PROCESSING"
        val PROCESSING_TTL: Duration = Duration.ofSeconds(30)
        val COMPLETED_TTL: Duration = Duration.ofHours(24)
    }
}
