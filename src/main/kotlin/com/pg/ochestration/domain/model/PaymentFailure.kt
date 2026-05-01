package com.pg.ochestration.domain.model

data class PaymentFailure(
    val code: String,
    val category: FailureCategory,
    val message: String
)
