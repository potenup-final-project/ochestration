package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.application.usecase.command.IssueApiKeyCommand
import com.pg.ochestration.application.usecase.result.ApproveLiveUpgradeResult
import com.pg.ochestration.domain.exception.MerchantNotFoundException
import com.pg.ochestration.domain.exception.MerchantNotEligibleForLiveException
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.MerchantStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ApproveLiveUpgradeUseCase(
    private val merchantRepository: MerchantRepository,
    private val issueApiKeyUseCase: IssueApiKeyUseCase
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun approve(merchantId: String): ApproveLiveUpgradeResult {
        val merchant = merchantRepository.findById(merchantId)
            ?: throw MerchantNotFoundException(merchantId)

        if (merchant.status != MerchantStatus.LIVE_PENDING) {
            throw MerchantNotEligibleForLiveException(
                merchantId = merchantId,
                status = merchant.status,
                reason = "LIVE_PENDING 상태에서만 Live 승인이 가능합니다"
            )
        }

        val updated = merchant.applyLiveApproval()
        merchantRepository.save(updated)

        val liveKeyResult = issueApiKeyUseCase.issue(
            IssueApiKeyCommand(
                merchantId = merchantId,
                environment = ApiKeyEnvironment.LIVE,
                description = "Live 승인 자동 발급"
            )
        )

        log.info("Live 전환 승인 완료 — merchantId={}, liveKeyId={}", merchantId, liveKeyResult.apiKey.keyId)
        return ApproveLiveUpgradeResult(
            merchantId = merchantId,
            status = updated.status,
            liveKeyId = liveKeyResult.apiKey.keyId
        )
    }
}
