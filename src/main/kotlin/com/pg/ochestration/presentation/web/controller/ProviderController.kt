package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.application.service.ProviderManagementService
import com.pg.ochestration.domain.exception.EnvironmentMismatchForOnboardingException
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import com.pg.ochestration.presentation.web.controller.request.ProviderConnectRequest
import com.pg.ochestration.presentation.web.controller.request.ProviderHealthUpdateRequest
import com.pg.ochestration.presentation.web.controller.response.ProviderConnectionResponse
import com.pg.ochestration.presentation.web.controller.response.ProviderDisconnectResponse
import com.pg.ochestration.presentation.web.controller.response.ProviderHealthResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/providers")
class ProviderController(
    private val providerManagementService: ProviderManagementService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/connect")
    fun connect(
        @RequestBody request: ProviderConnectRequest,
        principal: MerchantPrincipal
    ): ProviderConnectionResponse {
        if (principal.environment == ApiKeyEnvironment.SANDBOX) {
            throw EnvironmentMismatchForOnboardingException("LIVE", "SANDBOX")
        }
        val connected = providerManagementService.connect(request.toCommand(principal.merchantId))
        return ProviderConnectionResponse.from(connected)
    }

    @GetMapping
    fun getProviders(principal: MerchantPrincipal): List<ProviderConnectionResponse> {
        log.debug("Provider 목록 조회: merchantId={}", principal.merchantId)
        return providerManagementService.listConnections().map {
            ProviderConnectionResponse.from(it)
        }
    }

    @DeleteMapping("/{provider}")
    fun disconnect(
        @PathVariable provider: Provider,
        principal: MerchantPrincipal
    ): ProviderDisconnectResponse {
        if (principal.environment == ApiKeyEnvironment.SANDBOX) {
            throw EnvironmentMismatchForOnboardingException("LIVE", "SANDBOX")
        }
        val disconnected = providerManagementService.disconnect(principal.merchantId, provider)
        return ProviderDisconnectResponse.from(disconnected)
    }

    @GetMapping("/capabilities")
    fun capabilities(): List<Map<String, Any>> {
        return providerManagementService.listCapabilities().map { (provider, capability) ->
            mapOf(
                "provider" to provider,
                "capabilities" to capability
            )
        }
    }

    @GetMapping("/health")
    fun health(): List<ProviderHealthResponse> {
        return providerManagementService.listHealth().map { (provider, health) ->
            ProviderHealthResponse(provider = provider, health = health)
        }
    }

    @PostMapping("/health")
    fun updateHealth(@RequestBody request: ProviderHealthUpdateRequest): ProviderHealthResponse {
        val updated = providerManagementService.updateHealth(request.provider, request.health)
        return ProviderHealthResponse(provider = request.provider, health = updated)
    }
}
