package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.EmailPort
import com.pg.ochestration.application.port.out.EmailVerificationTokenRepository
import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.domain.exception.DuplicateEmailException
import com.pg.ochestration.domain.model.EmailVerificationToken
import com.pg.ochestration.domain.model.Merchant
import com.pg.ochestration.domain.model.MerchantStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RegisterMerchantUseCaseTest {

    private val fakeMerchantRepository = FakeMerchantRepository()
    private val fakeEmailTokenRepository = FakeEmailVerificationTokenRepository()
    private val fakeEmailPort = FakeEmailPort()

    private val useCase = RegisterMerchantUseCase(
        merchantRepository = fakeMerchantRepository,
        emailVerificationTokenRepository = fakeEmailTokenRepository,
        emailPort = fakeEmailPort,
        emailTokenTtlMinutes = 30L
    )

    @Test
    fun `중복 이메일로 등록 시 DuplicateEmailException 발생`() {
        fakeMerchantRepository.existingEmails.add("duplicate@example.com")

        val exception = assertThrows<DuplicateEmailException> {
            useCase.register(
                email = "duplicate@example.com",
                password = "password123",
                businessName = null
            )
        }
        assertEquals("DUPLICATE_EMAIL", exception.errorCode)
    }

    @Test
    fun `정상 등록 시 PENDING 상태의 가맹점이 저장된다`() {
        val result = useCase.register(
            email = "new@example.com",
            password = "password123",
            businessName = "테스트 가맹점"
        )

        assertEquals("new@example.com", result.email)
        assertEquals(MerchantStatus.PENDING, result.status)
        assertNotNull(result.merchantId)

        val saved = fakeMerchantRepository.findById(result.merchantId)
        assertNotNull(saved)
        assertEquals(MerchantStatus.PENDING, saved.status)
        assertEquals("new@example.com", saved.email)
        assertEquals("테스트 가맹점", saved.businessName)
    }

    @Test
    fun `정상 등록 시 이메일 인증 토큰이 저장된다`() {
        val result = useCase.register(
            email = "new@example.com",
            password = "password123",
            businessName = null
        )

        assertEquals(1, fakeEmailTokenRepository.savedTokens.size)
        val token = fakeEmailTokenRepository.savedTokens.first()
        assertEquals(result.merchantId, token.merchantId)
    }

    @Test
    fun `정상 등록 시 이메일 발송이 1회 호출된다`() {
        useCase.register(
            email = "new@example.com",
            password = "password123",
            businessName = null
        )

        assertEquals(1, fakeEmailPort.sendCallCount)
        assertEquals("new@example.com", fakeEmailPort.lastSentEmail)
    }
}

// ---------------------------------------------------------------------------
// Fake 구현체
// ---------------------------------------------------------------------------

private class FakeMerchantRepository : MerchantRepository {
    val existingEmails: MutableSet<String> = mutableSetOf()
    private val store: MutableMap<String, Merchant> = mutableMapOf()

    override fun save(merchant: Merchant): Merchant {
        store[merchant.merchantId] = merchant
        return merchant
    }

    override fun findById(merchantId: String): Merchant? = store[merchantId]

    override fun findByEmail(email: String): Merchant? =
        store.values.firstOrNull { it.email == email }

    override fun existsByEmail(email: String): Boolean =
        existingEmails.contains(email) || store.values.any { it.email == email }
}

private class FakeEmailVerificationTokenRepository : EmailVerificationTokenRepository {
    val savedTokens: MutableList<EmailVerificationToken> = mutableListOf()

    override fun save(token: EmailVerificationToken): EmailVerificationToken {
        savedTokens.removeIf { it.tokenId == token.tokenId }
        savedTokens.add(token)
        return token
    }

    override fun findByHash(tokenHash: String): EmailVerificationToken? =
        savedTokens.firstOrNull { it.tokenHash == tokenHash }
}

private class FakeEmailPort : EmailPort {
    var sendCallCount: Int = 0
    var lastSentEmail: String? = null

    override fun sendEmailVerification(email: String, token: String, merchantId: String) {
        sendCallCount++
        lastSentEmail = email
    }
}
