package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.application.service.UnifiedPaymentService
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import com.pg.ochestration.presentation.web.dto.PaymentApproveRequest
import com.pg.ochestration.presentation.web.dto.PaymentCancelRequest
import com.pg.ochestration.presentation.web.dto.PaymentCancelResponse
import com.pg.ochestration.presentation.web.dto.PaymentView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val unifiedPaymentService: UnifiedPaymentService
) {
    @PostMapping("/approve")
    suspend fun approve(
        @RequestBody request: PaymentApproveRequest,
        principal: MerchantPrincipal
    ): PaymentView {
        val payment = unifiedPaymentService.approve(
            merchantId = principal.merchantId,
            orderId = request.orderId,
            amount = request.amount,
            currency = request.currency,
            idempotencyKey = request.idempotencyKey,
            requestedAt = request.requestedAt,
            preferredPrimaryProvider = request.preferredPrimaryProvider,
            metadata = request.metadata
        )
        return PaymentView.from(payment)
    }

    @GetMapping("/{paymentId}")
    suspend fun getPayment(
        @PathVariable paymentId: String,
        principal: MerchantPrincipal
    ): PaymentView {
        return PaymentView.from(unifiedPaymentService.getPayment(principal.merchantId, paymentId))
    }

    @PostMapping("/{paymentId}/cancel")
    suspend fun cancel(
        @PathVariable paymentId: String,
        @RequestBody request: PaymentCancelRequest,
        principal: MerchantPrincipal
    ): PaymentCancelResponse {
        return unifiedPaymentService.cancel(
            merchantId = principal.merchantId,
            paymentId = paymentId,
            reason = request.reason,
            idempotencyKey = request.idempotencyKey,
            requestedAt = request.requestedAt
        )
    }
}
