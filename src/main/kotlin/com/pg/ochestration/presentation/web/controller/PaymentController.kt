package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.application.service.UnifiedPaymentService
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import com.pg.ochestration.presentation.web.controller.request.PaymentApproveRequest
import com.pg.ochestration.presentation.web.controller.request.PaymentCancelRequest
import com.pg.ochestration.presentation.web.controller.response.PaymentCancelResponse
import com.pg.ochestration.presentation.web.controller.response.PaymentView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
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
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        principal: MerchantPrincipal
    ): PaymentView {
        val payment = unifiedPaymentService.approve(request.toCommand(principal, idempotencyKey))
        return PaymentView.from(payment)
    }

    @GetMapping("/{paymentId}")
    suspend fun getPayment(
        @PathVariable paymentId: String,
        principal: MerchantPrincipal
    ): PaymentView =
        PaymentView.from(unifiedPaymentService.getPayment(principal.merchantId, paymentId))

    @PostMapping("/{paymentId}/cancel")
    suspend fun cancel(
        @PathVariable paymentId: String,
        @RequestBody request: PaymentCancelRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        principal: MerchantPrincipal
    ): PaymentCancelResponse =
        PaymentCancelResponse.from(
            unifiedPaymentService.cancel(request.toCommand(principal, paymentId, idempotencyKey))
        )
}
