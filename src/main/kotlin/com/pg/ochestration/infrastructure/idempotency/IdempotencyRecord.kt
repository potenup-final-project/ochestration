package com.pg.ochestration.infrastructure.idempotency

data class IdempotencyRecord(
    val status: IdempotencyStatus,
    val httpStatus: Int? = null,
    val body: String? = null
)
