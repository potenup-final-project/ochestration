package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.EmailVerificationTokenRepository
import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.application.port.out.OnboardingTokenRepository
import com.pg.ochestration.application.usecase.result.VerifyEmailResult
import com.pg.ochestration.domain.exception.InvalidEmailTokenException
import com.pg.ochestration.domain.exception.MerchantNotFoundException
import com.pg.ochestration.domain.model.OnboardingToken
import com.pg.ochestration.infrastructure.token.TokenHasher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class VerifyEmailUseCase(
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val merchantRepository: MerchantRepository,
    private val onboardingTokenRepository: OnboardingTokenRepository,
    @Value("\${onboarding.onboarding-token.ttl-minutes:10}") private val onboardingTokenTtlMinutes: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun verify(rawToken: String): VerifyEmailResult {
        val tokenHash = TokenHasher.sha256(rawToken)

        val emailToken = emailVerificationTokenRepository.findByHash(tokenHash)
            ?: throw InvalidEmailTokenException("유효하지 않은 이메일 인증 토큰입니다")

        emailToken.ensureValid()

        val merchant = merchantRepository.findById(emailToken.merchantId)
            ?: throw MerchantNotFoundException(emailToken.merchantId)

        emailVerificationTokenRepository.save(emailToken.consume())

        val updatedMerchant = merchant.applyEmailVerification()
        merchantRepository.save(updatedMerchant)

        val now = Instant.now()
        val rawOnboardingToken = UUID.randomUUID().toString()
        val onboardingTokenHash = TokenHasher.sha256(rawOnboardingToken)
        val expiresAt = now.plus(onboardingTokenTtlMinutes, ChronoUnit.MINUTES)

        val onboardingToken = OnboardingToken(
            tokenId = UUID.randomUUID().toString(),
            merchantId = merchant.merchantId,
            tokenHash = onboardingTokenHash,
            expiresAt = expiresAt
        )
        onboardingTokenRepository.save(onboardingToken)

        log.info("이메일 인증 완료 — merchantId={}, 상태={}", merchant.merchantId, updatedMerchant.status)
        return VerifyEmailResult(
            merchantId = merchant.merchantId,
            status = updatedMerchant.status,
            onboardingToken = rawOnboardingToken,
            expiresAt = expiresAt
        )
    }
}
