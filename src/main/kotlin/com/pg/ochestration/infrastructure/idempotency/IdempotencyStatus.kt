package com.pg.ochestration.infrastructure.idempotency

enum class IdempotencyStatus {
    PROCESSING,
    COMPLETED;

    fun isCompleted(): Boolean = this == COMPLETED
}
