package com.pg.ochestration.infrastructure.idempotency

import com.pg.ochestration.infrastructure.idempotency.IdempotencyStatus.COMPLETED
import com.pg.ochestration.infrastructure.idempotency.IdempotencyStatus.PROCESSING
import com.pg.ochestration.infrastructure.persistence.jpa.IdempotencyRecordJpaRepository
import com.pg.ochestration.infrastructure.persistence.jpa.entity.IdempotencyRecordJpaEntity
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class IdempotencyRecordDbStore(
    private val jpaRepository: IdempotencyRecordJpaRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun find(redisKey: String, includeProcessing: Boolean): IdempotencyRecord? =
        jpaRepository.findByRedisKey(redisKey)
            ?.takeUnless(::isExpired)
            ?.takeIf { includeProcessing || it.status.isCompleted() }
            ?.toRecord()

    @Transactional
    fun saveProcessingIfAbsent(context: IdempotencyContext) {
        val redisKey = context.redisKey()
        if (jpaRepository.findByRedisKey(redisKey) != null) return

        runCatching {
            jpaRepository.save(newProcessingEntity(context))
        }.onFailure { ex ->
            if (ex !is DataIntegrityViolationException) {
                log.warn("멱등성 PROCESSING DB 저장 실패: redisKey={}, error={}", redisKey, ex.message)
            }
        }
    }

    @Transactional
    fun acquire(context: IdempotencyContext): Boolean {
        val redisKey = context.redisKey()
        val existing = jpaRepository.findByRedisKey(redisKey)
        if (existing != null && !isExpired(existing)) return false
        if (existing != null) {
            jpaRepository.delete(existing)
        }

        return runCatching {
            jpaRepository.save(newProcessingEntity(context))
            true
        }.recover { ex ->
            if (ex is DataIntegrityViolationException) {
                false
            } else {
                throw ex
            }
        }.getOrThrow()
    }

    @Transactional
    fun complete(context: IdempotencyContext, httpStatus: Int, body: String) {
        val redisKey = context.redisKey()
        val entity = jpaRepository.findByRedisKey(redisKey)
            ?: newProcessingEntity(context)
        entity.complete(httpStatus, body)
        jpaRepository.save(entity)
    }

    private fun newProcessingEntity(context: IdempotencyContext): IdempotencyRecordJpaEntity =
        IdempotencyRecordJpaEntity(
            recordId = UUID.randomUUID().toString(),
            redisKey = context.redisKey(),
            merchantId = context.merchantId,
            idempotencyKey = context.idempotencyKey,
            operation = context.operation,
            status = PROCESSING,
            createdAt = Instant.now()
        )

    private fun isExpired(entity: IdempotencyRecordJpaEntity): Boolean {
        val now = Instant.now()
        return when (entity.status) {
            PROCESSING -> entity.createdAt.plus(PROCESSING_TTL).isBefore(now)
            COMPLETED -> entity.completedAt
                ?.plus(COMPLETED_TTL)
                ?.isBefore(now)
                ?: false
        }
    }

    private fun IdempotencyRecordJpaEntity.toRecord(): IdempotencyRecord =
        IdempotencyRecord(
            status = status,
            httpStatus = httpStatus,
            body = responseBody
        )

    private companion object {
        val PROCESSING_TTL: Duration = Duration.ofSeconds(30)
        val COMPLETED_TTL: Duration = Duration.ofHours(24)
    }
}
