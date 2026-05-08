package com.pg.ochestration.infrastructure.config

import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.application.usecase.command.IssueApiKeyCommand
import com.pg.ochestration.application.usecase.IssueApiKeyUseCase
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.Merchant
import com.pg.ochestration.domain.model.MerchantStatus
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.time.Instant

@Profile("!prod")
@Component
class DataSeeder(
    private val merchantRepository: MerchantRepository,
    private val issueApiKeyUseCase: IssueApiKeyUseCase,
    private val passwordEncoder: BCryptPasswordEncoder
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val SEED_MERCHANT_ID = "merchant-001"
        private const val SEED_MERCHANT_EMAIL = "merchant-001@dev.local"
        private const val SEED_MERCHANT_PASSWORD = "dev-password"
    }

    override fun run(args: ApplicationArguments) {
        seedMerchant()
    }

    private fun seedMerchant() {
        if (merchantRepository.existsByEmail(SEED_MERCHANT_EMAIL)) {
            log.debug("시드 가맹점이 이미 존재합니다 — email={}", SEED_MERCHANT_EMAIL)
            return
        }

        val now = Instant.now()
        val merchant = Merchant(
            merchantId = SEED_MERCHANT_ID,
            email = SEED_MERCHANT_EMAIL,
            passwordHash = passwordEncoder.encode(SEED_MERCHANT_PASSWORD).toString(),
            businessName = "개발 테스트 가맹점",
            businessRegistrationNumber = null,
            businessRegistrationFileUrl = null,
            status = MerchantStatus.LIVE_ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        merchantRepository.save(merchant)

        val sandboxKeyResult = issueApiKeyUseCase.issue(
            IssueApiKeyCommand(
                merchantId = SEED_MERCHANT_ID,
                environment = ApiKeyEnvironment.SANDBOX,
                description = "개발 Sandbox Key (자동 시드)"
            )
        )

        log.info(
            "시드 가맹점 및 API Key 생성 완료 — merchantId={}, sandboxKey={}",
            SEED_MERCHANT_ID, sandboxKeyResult.rawKey
        )
    }
}
