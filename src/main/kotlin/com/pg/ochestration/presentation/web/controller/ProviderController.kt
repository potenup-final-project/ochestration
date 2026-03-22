package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.presentation.web.dto.ProviderConnectRequest
import com.pg.ochestration.presentation.web.dto.ProviderConnectionResponse
import com.pg.ochestration.presentation.web.dto.ProviderDisconnectResponse
import com.pg.ochestration.presentation.web.dto.ProviderHealthResponse
import com.pg.ochestration.presentation.web.dto.ProviderHealthUpdateRequest
import com.pg.ochestration.application.service.ProviderManagementService
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
    private val merchantId = "merchant-001"

    @PostMapping("/connect")
    fun connect(@RequestBody request: ProviderConnectRequest): ProviderConnectionResponse {
        val connected = providerManagementService.connect(merchantId, request)
        return ProviderConnectionResponse(
            providerConnectionId = connected.providerConnectionId,
            merchantId = connected.merchantId,
            provider = connected.provider,
            displayName = connected.displayName,
            status = connected.status
        )
    }

    @GetMapping
    fun getProviders(): List<ProviderConnectionResponse> {
        return providerManagementService.listConnections().map {
            ProviderConnectionResponse(
                providerConnectionId = it.providerConnectionId,
                merchantId = it.merchantId,
                provider = it.provider,
                displayName = it.displayName,
                status = it.status
            )
        }
    }

    @DeleteMapping("/{provider}")
    fun disconnect(@PathVariable provider: Provider): ProviderDisconnectResponse {
        val disconnected = providerManagementService.disconnect(merchantId, provider)
        return ProviderDisconnectResponse(
            provider = disconnected.provider,
            merchantId = disconnected.merchantId,
            status = disconnected.status
        )
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
