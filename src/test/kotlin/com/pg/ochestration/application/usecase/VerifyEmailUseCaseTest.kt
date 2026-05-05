package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.EmailVerificationTokenRepository
import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.application.port.out.OnboardingTokenRepository
import com.pg.ochestration.domain.exception.InvalidEmailTokenException
import com.pg.ochestration.domain.model.EmailVerificationToken
import com.pg.ochestration.domain.model.Merchant
import com.pg.ochestration.domain.model.MerchantStatus
import com.pg.ochestration.domain.model.OnboardingToken
import com.pg.ochestration.infrastructure.token.TokenHasher
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VerifyEmailUseCaseTest {

    private val fakeMerchantRepository = VerifyFakeMerchantRepository()
    private val fakeEmailTokenRepository = VerifyFakeEmailVerificationTokenRepository()
    private val fakeOnboardingTokenRepository = VerifyFakeOnboardingTokenRepository()

    private val useCase = VerifyEmailUseCase(
        emailVerificationTokenRepository = fakeEmailTokenRepository,
        merchantRepository = fakeMerchantRepository,
        onboardingTokenRepository = fakeOnboardingTokenRepository,
        onboardingTokenTtlMinutes = 10L
    )

    @Test
    fun `유효한 이메일 인증 토큰으로 검증 시 SANDBOX_ACTIVE 상태로 전환되고 온보딩 토큰이 반환된다`() {
        val merchantId = UUID.randomUUID().toString()
        val rawToken = "valid-raw-token-for-verify"
        setupMerchantWithToken(merchantId, rawToken, expiredAt = Instant.now().plus(30, ChronoUnit.MINUTES))

        val result = useCase.verify(rawToken)

        assertEquals(merchantId, result.merchantId)
        assertEquals(MerchantStatus.SANDBOX_ACTIVE, result.status)
        assertNotNull(result.onboardingToken)
        assert(result.expiresAt.isAfter(Instant.now()))

        val updatedMerchant = fakeMerchantRepository.findById(merchantId)
        assertEquals(MerchantStatus.SANDBOX_ACTIVE, updatedMerchant?.status)
    }

    @Test
    fun `만료된 이메일 인증 토큰으로 검증 시 InvalidEmailTokenException 발생`() {
        val merchantId = UUID.randomUUID().toString()
        val rawToken = "expired-raw-token"
        setupMerchantWithToken(merchantId, rawToken, expiredAt = Instant.now().minus(1, ChronoUnit.MINUTES))

        val exception = assertThrows<InvalidEmailTokenException> {
            useCase.verify(rawToken)
        }
        assertEquals("INVALID_EMAIL_TOKEN", exception.errorCode)
    }

    @Test
    fun `이미 사용된 이메일 인증 토큰으로 검증 시 InvalidEmailTokenException 발생`() {
        val merchantId = UUID.randomUUID().toString()
        val rawToken = "used-raw-token"
        setupMerchantWithToken(
            merchantId,
            rawToken,
            expiredAt = Instant.now().plus(30, ChronoUnit.MINUTES),
            used = true
        )

        val exception = assertThrows<InvalidEmailTokenException> {
            useCase.verify(rawToken)
        }
        assertEquals("INVALID_EMAIL_TOKEN", exception.errorCode)
    }

    @Test
    fun `이메일 인증 성공 후 온보딩 토큰이 저장된다`() {
        val merchantId = UUID.randomUUID().toString()
        val rawToken = "valid-token-for-onboarding"
        setupMerchantWithToken(merchantId, rawToken, expiredAt = Instant.now().plus(30, ChronoUnit.MINUTES))

        useCase.verify(rawToken)

        assertEquals(1, fakeOnboardingTokenRepository.savedTokens.size)
        val savedToken = fakeOnboardingTokenRepository.savedTokens.first()
        assertEquals(merchantId, savedToken.merchantId)
    }

    private fun setupMerchantWithToken(
        merchantId: String,
        rawToken: String,
        expiredAt: Instant,
        used: Boolean = false
    ) {
        val now = Instant.now()
        val merchant = Merchant(
            merchantId = merchantId,
            email = "$merchantId@test.com",
            passwordHash = "hashed",
            businessName = null,
            businessRegistrationNumber = null,
            businessRegistrationFileUrl = null,
            status = MerchantStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
        fakeMerchantRepository.save(merchant)

        val tokenHash = TokenHasher.sha256(rawToken)
        val emailToken = EmailVerificationToken(
            tokenId = UUID.randomUUID().toString(),
            merchantId = merchantId,
            tokenHash = tokenHash,
            expiresAt = expiredAt,
            used = used
        )
        fakeEmailTokenRepository.save(emailToken)
    }
}

// ---------------------------------------------------------------------------
// Fake 구현체
// ---------------------------------------------------------------------------

private class VerifyFakeMerchantRepository : MerchantRepository {
    private val store: MutableMap<String, Merchant> = mutableMapOf()

    override fun save(merchant: Merchant): Merchant {
        store[merchant.merchantId] = merchant
        return merchant
    }

    override fun findById(merchantId: String): Merchant? = store[merchantId]
    override fun findByIdForUpdate(merchantId: String): Merchant? = store[merchantId]
    override fun findByEmail(email: String): Merchant? = store.values.firstOrNull { it.email == email }
    override fun existsByEmail(email: String): Boolean = store.values.any { it.email == email }
}

private class VerifyFakeEmailVerificationTokenRepository : EmailVerificationTokenRepository {
    private val store: MutableMap<String, EmailVerificationToken> = mutableMapOf()

    override fun save(token: EmailVerificationToken): EmailVerificationToken {
        store[token.tokenHash] = token
        return token
    }

    override fun findByHash(tokenHash: String): EmailVerificationToken? = store[tokenHash]
}

private class VerifyFakeOnboardingTokenRepository : OnboardingTokenRepository {
    val savedTokens: MutableList<OnboardingToken> = mutableListOf()

    override fun save(token: OnboardingToken): OnboardingToken {
        savedTokens.add(token)
        return token
    }

    override fun consumeByRawToken(rawToken: String): OnboardingToken {
        val tokenHash = TokenHasher.sha256(rawToken)
        return savedTokens.firstOrNull { it.tokenHash == tokenHash }
            ?: error("토큰을 찾을 수 없습니다: $rawToken")
    }
}
