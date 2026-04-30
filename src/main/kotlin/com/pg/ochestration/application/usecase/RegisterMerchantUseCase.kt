package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.EmailPort
import com.pg.ochestration.application.port.out.EmailVerificationTokenRepository
import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.domain.exception.DuplicateEmailException
import com.pg.ochestration.domain.model.EmailVerificationToken
import com.pg.ochestration.domain.model.Merchant
import com.pg.ochestration.domain.model.MerchantStatus
import com.pg.ochestration.infrastructure.token.TokenHasher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class RegisterMerchantResult(
    val merchantId: String,
    val email: String,
    val status: MerchantStatus
)

@Service
class RegisterMerchantUseCase(
    private val merchantRepository: MerchantRepository,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val emailPort: EmailPort,
    @Value("\${onboarding.email-token.ttl-minutes:30}") private val emailTokenTtlMinutes: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val passwordEncoder = BCryptPasswordEncoder()

    @Transactional
    fun register(email: String, password: String, businessName: String?): RegisterMerchantResult {
        if (merchantRepository.existsByEmail(email)) {
            throw DuplicateEmailException(email)
        }

        val merchantId = UUID.randomUUID().toString()
        val passwordHash = passwordEncoder.encode(password).toString()
        val now = Instant.now()

        val merchant = Merchant(
            merchantId = merchantId,
            email = email,
            passwordHash = passwordHash,
            businessName = businessName,
            businessRegistrationNumber = null,
            businessRegistrationFileUrl = null,
            status = MerchantStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
        merchantRepository.save(merchant)

        val rawToken = UUID.randomUUID().toString()
        val tokenHash = TokenHasher.sha256(rawToken)
        val emailToken = EmailVerificationToken(
            tokenId = UUID.randomUUID().toString(),
            merchantId = merchantId,
            tokenHash = tokenHash,
            expiresAt = now.plus(emailTokenTtlMinutes, ChronoUnit.MINUTES)
        )
        emailVerificationTokenRepository.save(emailToken)

        // 트랜잭션 커밋 후 이메일 발송 (fire-and-forget, 실패 무시)
        runCatching {
            emailPort.sendEmailVerification(email, rawToken, merchantId)
        }.onFailure { ex ->
            log.warn("이메일 인증 토큰 발송 실패 — merchantId={}, email={}, 원인={}", merchantId, email, ex.message)
        }

        log.info("가맹점 회원가입 완료 — merchantId={}, email={}", merchantId, email)
        return RegisterMerchantResult(merchantId = merchantId, email = email, status = MerchantStatus.PENDING)
    }
}
