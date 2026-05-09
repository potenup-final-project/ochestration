package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.application.service.WebhookEndpointService
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import com.pg.ochestration.presentation.web.controller.request.CreateWebhookEndpointRequest
import com.pg.ochestration.presentation.web.controller.request.UpdateWebhookEndpointRequest
import com.pg.ochestration.presentation.web.controller.response.WebhookEndpointCreateResponse
import com.pg.ochestration.presentation.web.controller.response.WebhookEndpointView
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/webhook-endpoints")
class WebhookEndpointController(
    private val webhookEndpointService: WebhookEndpointService
) {

    @PostMapping
    fun create(
        @RequestBody request: CreateWebhookEndpointRequest,
        principal: MerchantPrincipal
    ): ResponseEntity<WebhookEndpointCreateResponse> {
        val result = webhookEndpointService.create(
            merchantId = principal.merchantId,
            url = request.url,
            description = request.description
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(WebhookEndpointCreateResponse.from(result))
    }

    @GetMapping
    fun list(principal: MerchantPrincipal): List<WebhookEndpointView> =
        webhookEndpointService.list(principal.merchantId).map { WebhookEndpointView.from(it) }

    @GetMapping("/{endpointId}")
    fun get(
        @PathVariable endpointId: String,
        principal: MerchantPrincipal
    ): WebhookEndpointView =
        WebhookEndpointView.from(webhookEndpointService.get(principal.merchantId, endpointId))

    @PatchMapping("/{endpointId}")
    fun update(
        @PathVariable endpointId: String,
        @RequestBody request: UpdateWebhookEndpointRequest,
        principal: MerchantPrincipal
    ): WebhookEndpointView =
        WebhookEndpointView.from(
            webhookEndpointService.update(
                request.toCommand(merchantId = principal.merchantId, endpointId = endpointId)
            )
        )

    @DeleteMapping("/{endpointId}")
    fun delete(
        @PathVariable endpointId: String,
        principal: MerchantPrincipal
    ): WebhookEndpointView =
        WebhookEndpointView.from(webhookEndpointService.deactivate(principal.merchantId, endpointId))
}
