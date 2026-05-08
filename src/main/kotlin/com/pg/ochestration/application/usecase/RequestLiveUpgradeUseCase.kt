package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.application.port.out.ProviderConnectionCountPort
import com.pg.ochestration.application.usecase.result.RequestLiveUpgradeResult
import com.pg.ochestration.domain.exception.MerchantNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RequestLiveUpgradeUseCase(
    private val merchantRepository: MerchantRepository,
    private val providerConnectionCountPort: ProviderConnectionCountPort
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

        val connectedCount = providerConnectionCountPort.countConnected()
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
