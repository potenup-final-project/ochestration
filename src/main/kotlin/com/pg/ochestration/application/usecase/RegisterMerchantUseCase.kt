package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.EmailPort
import com.pg.ochestration.application.port.out.EmailVerificationTokenRepository
import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.application.usecase.result.RegisterMerchantResult
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
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class RegisterMerchantUseCase(
    private val merchantRepository: MerchantRepository,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val emailPort: EmailPort,
    private val passwordEncoder: BCryptPasswordEncoder,
    @Value("\${onboarding.email-token.ttl-minutes:30}") private val emailTokenTtlMinutes: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

        // DB 커밋 이후에 이메일 발송 (fire-and-forget, 실패 무시)
        val sendEmail = {
            runCatching {
                emailPort.sendEmailVerification(email, rawToken, merchantId)
            }.onFailure { ex ->
                log.warn("이메일 인증 토큰 발송 실패 — merchantId={}, email={}, 원인={}", merchantId, email, ex.message)
            }
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() { sendEmail() }
            })
        } else {
            sendEmail()
        }

        log.info("가맹점 회원가입 완료 — merchantId={}, email={}", merchantId, email)
        return RegisterMerchantResult(merchantId = merchantId, email = email, status = MerchantStatus.PENDING)
    }
}
