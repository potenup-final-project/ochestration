package com.pg.ochestration.application.orchestration

import com.pg.ochestration.domain.model.FilteredOutProvider
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderFilteredOutReason
import com.pg.ochestration.domain.model.ProviderSelectionResult
import com.pg.ochestration.domain.model.SelectionPrimaryReason
import com.pg.ochestration.domain.service.ProviderCapabilityRegistry
import com.pg.ochestration.domain.model.ConnectionStatus
import com.pg.ochestration.domain.model.ProviderHealthStatus
import com.pg.ochestration.infrastructure.persistence.jpa.ProviderConnectionRepository
import com.pg.ochestration.infrastructure.persistence.jpa.ProviderHealthRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ProviderSelectionPolicy(
    private val connectionRepository: ProviderConnectionRepository,
    private val healthRepository: ProviderHealthRepository,
    private val capabilityRegistry: ProviderCapabilityRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val defaultPriority = listOf(Provider.TOSS, Provider.KAKAOPAY, Provider.INICIS)

    fun selectForApprove(merchantId: String, preferredPrimaryProvider: Provider?): ProviderSelectionResult {
        val priority = buildPriority(preferredPrimaryProvider)
        logger.info("[Selection] merchantId={} initialCandidates={}", merchantId, priority)

        val filteredOut = mutableListOf<FilteredOutProvider>()
        val candidates = mutableListOf<Provider>()

        priority.forEach { provider ->
            if (!isConnected(provider)) {
                filteredOut += FilteredOutProvider(provider, ProviderFilteredOutReason.NOT_CONNECTED)
                logger.info("[Selection] provider={} excluded reason={}", provider, ProviderFilteredOutReason.NOT_CONNECTED)
                return@forEach
            }

            if (!isHealthy(provider)) {
                filteredOut += FilteredOutProvider(provider, ProviderFilteredOutReason.PROVIDER_UNHEALTHY)
                logger.info("[Selection] provider={} excluded reason={}", provider, ProviderFilteredOutReason.PROVIDER_UNHEALTHY)
                return@forEach
            }

            if (!supportsApprove(provider)) {
                filteredOut += FilteredOutProvider(provider, ProviderFilteredOutReason.CAPABILITY_NOT_SUPPORTED)
                logger.info("[Selection] provider={} excluded reason={}", provider, ProviderFilteredOutReason.CAPABILITY_NOT_SUPPORTED)
                return@forEach
            }

            candidates += provider
        }

        val selectedPrimary = candidates.firstOrNull()
        val selectedReason = when {
            selectedPrimary != null && preferredPrimaryProvider != null && selectedPrimary == preferredPrimaryProvider ->
                SelectionPrimaryReason.USER_PREFERRED

            selectedPrimary != null && preferredPrimaryProvider != null ->
                SelectionPrimaryReason.USER_PREFERRED_EXCLUDED_FALLBACK

            selectedPrimary != null ->
                SelectionPrimaryReason.HIGHEST_PRIORITY_DEFAULT

            else ->
                SelectionPrimaryReason.NO_ELIGIBLE_PROVIDER
        }

        logger.info("[Selection] selectedPrimaryProvider={} reason={}", selectedPrimary, selectedReason)

        return ProviderSelectionResult(
            initialCandidates = priority,
            filteredOutProviders = filteredOut,
            candidates = candidates,
            selectedPrimaryProvider = selectedPrimary,
            selectedPrimaryReason = selectedReason
        )
    }

    private fun buildPriority(preferredPrimaryProvider: Provider?): List<Provider> {
        if (preferredPrimaryProvider == null) return defaultPriority
        return listOf(preferredPrimaryProvider) + defaultPriority.filter { it != preferredPrimaryProvider }
    }

    private fun isConnected(provider: Provider): Boolean =
        connectionRepository.getConnectionStatus(provider) == ConnectionStatus.CONNECTED

    private fun isHealthy(provider: Provider): Boolean =
        healthRepository.get(provider) == ProviderHealthStatus.HEALTHY

    private fun supportsApprove(provider: Provider): Boolean = capabilityRegistry.get(provider).approve
}
