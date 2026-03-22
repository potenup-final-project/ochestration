package com.pg.ochestration.infrastructure.bootstrap

import com.pg.ochestration.domain.model.ConnectionStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderHealthStatus
import com.pg.ochestration.infrastructure.persistence.memory.ProviderConnectionRepository
import com.pg.ochestration.infrastructure.persistence.memory.ProviderHealthRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class DataSeeder(
    private val connectionRepository: ProviderConnectionRepository,
    private val healthRepository: ProviderHealthRepository
) : CommandLineRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String) {
        val merchantId = "merchant-001"

        connectionRepository.upsert(merchantId, Provider.TOSS, "토스 주 결제", ConnectionStatus.CONNECTED)
        connectionRepository.upsert(merchantId, Provider.KAKAOPAY, "카카오페이 보조", ConnectionStatus.CONNECTED)
        connectionRepository.upsert(merchantId, Provider.INICIS, "이니시스 백업", ConnectionStatus.DISCONNECTED)

        healthRepository.set(Provider.TOSS, ProviderHealthStatus.HEALTHY)
        healthRepository.set(Provider.KAKAOPAY, ProviderHealthStatus.HEALTHY)
        healthRepository.set(Provider.INICIS, ProviderHealthStatus.HEALTHY)

        logger.info("[Seed] merchantId=merchant-001 connected=[TOSS,KAKAOPAY] disconnected=[INICIS]")
    }
}
