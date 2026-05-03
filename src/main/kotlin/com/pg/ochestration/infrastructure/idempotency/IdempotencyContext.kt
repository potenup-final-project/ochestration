package com.pg.ochestration.infrastructure.idempotency

data class IdempotencyContext(
    val merchantId: String,
    val idempotencyKey: String,
    val operation: IdempotencyOperation
) {
    fun redisKey(): String = "idempotency:${operation.name}:$merchantId:$idempotencyKey"
}
