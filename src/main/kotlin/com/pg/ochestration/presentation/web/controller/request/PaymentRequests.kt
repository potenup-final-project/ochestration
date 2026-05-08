package com.pg.ochestration.presentation.web.controller.request

import com.pg.ochestration.application.service.command.UnifiedPaymentApproveCommand
import com.pg.ochestration.application.service.command.UnifiedPaymentCancelCommand
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import java.time.Instant

data class PaymentApproveRequest(
    val orderId: String,
    val amount: Long,
    val currency: String = "KRW",
    val requestedAt: Instant? = null,
    val preferredPrimaryProvider: Provider? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toCommand(principal: MerchantPrincipal, idempotencyKey: String?): UnifiedPaymentApproveCommand =
        UnifiedPaymentApproveCommand(
            merchantId = principal.merchantId,
            environment = principal.environment,
            orderId = orderId,
            amount = amount,
            currency = currency,
            idempotencyKey = idempotencyKey,
            requestedAt = requestedAt,
            preferredPrimaryProvider = preferredPrimaryProvider,
            metadata = metadata
        )
}

data class PaymentCancelRequest(
    val reason: String,
    val requestedAt: Instant? = null
) {
    fun toCommand(
        principal: MerchantPrincipal,
        paymentId: String,
        idempotencyKey: String?
    ): UnifiedPaymentCancelCommand =
        UnifiedPaymentCancelCommand(
            merchantId = principal.merchantId,
            environment = principal.environment,
            paymentId = paymentId,
            reason = reason,
            idempotencyKey = idempotencyKey,
            requestedAt = requestedAt
        )
}
