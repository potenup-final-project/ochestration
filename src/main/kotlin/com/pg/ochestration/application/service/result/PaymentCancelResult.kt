package com.pg.ochestration.application.service.result

import com.pg.ochestration.domain.model.FailureCategory
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import java.time.Instant

data class PaymentCancelResult(
    val paymentId: String,
    val success: Boolean,
    val status: PaymentStatus,
    val provider: Provider,
    val providerTxId: String,
    val canceledAt: Instant?,
    val failure: PaymentFailureResult? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class PaymentFailureResult(
    val code: String,
    val category: FailureCategory,
    val message: String
)
