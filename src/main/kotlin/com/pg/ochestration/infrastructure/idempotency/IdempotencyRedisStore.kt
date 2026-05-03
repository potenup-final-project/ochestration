package com.pg.ochestration.infrastructure.idempotency

interface IdempotencyRedisStore {
    fun buildRedisKey(context: IdempotencyContext): String

    fun find(redisKey: String): IdempotencyRecord?

    fun tryAcquire(context: IdempotencyContext): Boolean

    fun complete(context: IdempotencyContext, httpStatus: Int, body: String)
}
