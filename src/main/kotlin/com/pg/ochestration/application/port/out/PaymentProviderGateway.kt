package com.pg.ochestration.application.port.out

import com.pg.ochestration.domain.model.FailureCategory
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import java.time.Instant

data class GatewayApproveCommand(
    val merchantId: String,
    val paymentId: String,
    val orderId: String,
    val amount: Long,
    val currency: String,
    val idempotencyKey: String,
    val requestedAt: Instant,
    val metadata: Map<String, String> = emptyMap()
)

data class GatewayCancelCommand(
    val merchantId: String,
    val paymentId: String,
    val providerTxId: String,
    val reason: String,
    val idempotencyKey: String,
    val requestedAt: Instant,
    val metadata: Map<String, String> = emptyMap()
)

data class GatewayPaymentQuery(
    val merchantId: String,
    val paymentId: String,
    val providerTxId: String,
    val metadata: Map<String, String> = emptyMap()
)

data class GatewayFailure(
    val code: String,
    val category: FailureCategory,
    val message: String
)

data class GatewayApproveResult(
    val success: Boolean,
    val provider: Provider,
    val providerTxId: String? = null,
    val approvedAt: Instant? = null,
    val status: PaymentStatus,
    val failure: GatewayFailure? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class GatewayCancelResult(
    val success: Boolean,
    val provider: Provider,
    val providerTxId: String,
    val canceledAt: Instant? = null,
    val status: PaymentStatus,
    val failure: GatewayFailure? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class GatewayPaymentResult(
    val success: Boolean,
    val provider: Provider,
    val providerTxId: String,
    val status: PaymentStatus,
    val approvedAt: Instant? = null,
    val canceledAt: Instant? = null,
    val failure: GatewayFailure? = null,
    val metadata: Map<String, String> = emptyMap()
)

interface PaymentProviderGateway {
    fun supports(provider: Provider): Boolean
    suspend fun approve(command: GatewayApproveCommand): GatewayApproveResult
    suspend fun cancel(command: GatewayCancelCommand): GatewayCancelResult
    suspend fun getPayment(query: GatewayPaymentQuery): GatewayPaymentResult
}
