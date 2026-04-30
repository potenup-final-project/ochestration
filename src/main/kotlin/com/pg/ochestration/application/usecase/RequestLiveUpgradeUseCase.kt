package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.domain.exception.MerchantNotFoundException
import com.pg.ochestration.domain.model.MerchantStatus
import com.pg.ochestration.infrastructure.persistence.jpa.ProviderConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class RequestLiveUpgradeResult(
    val merchantId: String,
    val status: MerchantStatus
)

@Service
class RequestLiveUpgradeUseCase(
    private val merchantRepository: MerchantRepository,
    private val providerConnectionRepository: ProviderConnectionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun request(
        merchantId: String,
        businessRegistrationNumber: String,
        businessRegistrationFileUrl: String
    ): RequestLiveUpgradeResult {
        val merchant = merchantRepository.findById(merchantId)
            ?: throw MerchantNotFoundException(merchantId)

        val connectedCount = providerConnectionRepository.countConnected()
        merchant.ensureEligibleForLiveUpgrade(connectedCount)

        val updated = merchant.applyLiveUpgradeRequest(
            businessRegistrationNumber = businessRegistrationNumber,
            businessRegistrationFileUrl = businessRegistrationFileUrl
        )
        merchantRepository.save(updated)

        log.info("Live 전환 신청 완료 — merchantId={}, 상태={}", merchantId, updated.status)
        return RequestLiveUpgradeResult(merchantId = merchantId, status = updated.status)
    }
}
