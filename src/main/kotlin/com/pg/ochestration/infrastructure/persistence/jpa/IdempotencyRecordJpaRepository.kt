package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.infrastructure.persistence.jpa.entity.IdempotencyRecordJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface IdempotencyRecordJpaRepository : JpaRepository<IdempotencyRecordJpaEntity, String> {
    fun findByRedisKey(redisKey: String): IdempotencyRecordJpaEntity?
}
