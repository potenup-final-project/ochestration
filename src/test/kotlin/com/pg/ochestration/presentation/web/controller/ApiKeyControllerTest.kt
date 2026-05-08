package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.application.usecase.command.IssueApiKeyCommand
import com.pg.ochestration.application.usecase.result.IssueApiKeyResult
import com.pg.ochestration.application.usecase.IssueApiKeyUseCase
import com.pg.ochestration.application.usecase.RevokeApiKeyUseCase
import com.pg.ochestration.domain.exception.InvalidApiKeyStateException
import com.pg.ochestration.domain.exception.InvalidOnboardingTokenException
import com.pg.ochestration.domain.exception.MissingApiKeyException
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.ApiKeyScope
import com.pg.ochestration.domain.model.ApiKeyStatus
import com.pg.ochestration.domain.model.MerchantApiKey
import com.pg.ochestration.infrastructure.auth.OnboardingTokenInterceptor
import com.pg.ochestration.presentation.web.controller.request.IssueApiKeyRequest
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ApiKeyControllerTest {

    private val issueUseCase: IssueApiKeyUseCase = mock(IssueApiKeyUseCase::class.java)
    private val revokeUseCase: RevokeApiKeyUseCase = mock(RevokeApiKeyUseCase::class.java)

    private val controller = ApiKeyController(
        issueApiKeyUseCase = issueUseCase,
        revokeApiKeyUseCase = revokeUseCase
    )

    // -------------------------------------------------------------------------
    // POST /api/auth/keys — 발급 (온보딩 토큰 기반)
    // -------------------------------------------------------------------------

    @Test
    fun `온보딩 토큰 attribute가 있을 때 201과 rawKey를 반환한다`() {
        val merchantId = "merchant-001"
        val request = IssueApiKeyRequest(environment = ApiKeyEnvironment.SANDBOX, description = null)
        val httpRequest = MockHttpServletRequest().apply {
            setAttribute(OnboardingTokenInterceptor.ATTR_KEY, merchantId)
        }
        val issuedKey = anApiKey(status = ApiKeyStatus.ACTIVE)
        val result = IssueApiKeyResult(apiKey = issuedKey, rawKey = RAW_KEY)
        `when`(issueUseCase.issue(IssueApiKeyCommand(merchantId, ApiKeyEnvironment.SANDBOX, null))).thenReturn(result)

        val response = controller.issueKey(request, httpRequest)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        assertEquals(RAW_KEY, response.body!!.rawKey)
        assertEquals(issuedKey.keyId, response.body!!.keyId)
        assertEquals(ApiKeyStatus.ACTIVE, response.body!!.status)
    }

    @Test
    fun `keyPrefix와 environment가 응답에 포함된다`() {
        val merchantId = "merchant-001"
        val request = IssueApiKeyRequest(environment = ApiKeyEnvironment.SANDBOX, description = "테스트 Key")
        val httpRequest = MockHttpServletRequest().apply {
            setAttribute(OnboardingTokenInterceptor.ATTR_KEY, merchantId)
        }
        val issuedKey = anApiKey(environment = ApiKeyEnvironment.SANDBOX)
        val result = IssueApiKeyResult(apiKey = issuedKey, rawKey = RAW_KEY)
        `when`(issueUseCase.issue(IssueApiKeyCommand(merchantId, ApiKeyEnvironment.SANDBOX, "테스트 Key"))).thenReturn(result)

        val response = controller.issueKey(request, httpRequest)

        assertEquals(issuedKey.keyPrefix, response.body!!.keyPrefix)
        assertEquals(ApiKeyEnvironment.SANDBOX, response.body!!.environment)
    }

    @Test
    fun `온보딩 토큰 attribute가 없으면 InvalidOnboardingTokenException 발생`() {
        val request = IssueApiKeyRequest(environment = ApiKeyEnvironment.SANDBOX, description = null)
        val httpRequest = MockHttpServletRequest() // attribute 없음

        assertFailsWith<InvalidOnboardingTokenException> {
            controller.issueKey(request, httpRequest)
        }
    }

    @Test
    fun `IssueApiKeyUseCase에서 예외 발생 시 그대로 전파된다`() {
        val merchantId = "merchant-001"
        val request = IssueApiKeyRequest(environment = ApiKeyEnvironment.SANDBOX, description = null)
        val httpRequest = MockHttpServletRequest().apply {
            setAttribute(OnboardingTokenInterceptor.ATTR_KEY, merchantId)
        }
        `when`(issueUseCase.issue(IssueApiKeyCommand(merchantId, ApiKeyEnvironment.SANDBOX, null)))
            .thenThrow(RuntimeException("발급 실패"))

        assertFailsWith<RuntimeException> {
            controller.issueKey(request, httpRequest)
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/auth/keys/{keyId} — 폐기
    // -------------------------------------------------------------------------

    @Test
    fun `유효한 X-Api-Key 헤더가 있을 때 폐기된 Key 응답을 반환한다`() {
        val revokedKey = anApiKey(keyId = "key-001", status = ApiKeyStatus.REVOKED, revokedAt = Instant.now())
        val httpRequest = MockHttpServletRequest().apply { addHeader("X-Api-Key", RAW_KEY) }
        `when`(revokeUseCase.revoke(keyId = "key-001", rawKey = RAW_KEY)).thenReturn(revokedKey)

        val response = controller.revokeKey(keyId = "key-001", httpRequest = httpRequest)

        assertEquals("key-001", response.keyId)
        assertEquals(ApiKeyStatus.REVOKED, response.status)
        assertNotNull(response.revokedAt)
    }

    @Test
    fun `X-Api-Key 헤더가 없으면 MissingApiKeyException 발생`() {
        val httpRequest = MockHttpServletRequest()

        assertFailsWith<MissingApiKeyException> {
            controller.revokeKey(keyId = "key-001", httpRequest = httpRequest)
        }
    }

    @Test
    fun `이미 폐기된 Key 폐기 시 InvalidApiKeyStateException 전파`() {
        val httpRequest = MockHttpServletRequest().apply { addHeader("X-Api-Key", RAW_KEY) }
        `when`(revokeUseCase.revoke(keyId = "key-001", rawKey = RAW_KEY))
            .thenThrow(InvalidApiKeyStateException("key-001", ApiKeyStatus.REVOKED))

        assertFailsWith<InvalidApiKeyStateException> {
            controller.revokeKey(keyId = "key-001", httpRequest = httpRequest)
        }
    }

    // -------------------------------------------------------------------------
    // Fixture
    // -------------------------------------------------------------------------

    private companion object {
        const val RAW_KEY = "sk_test_somerawkeyfortesting"

        fun anApiKey(
            keyId: String = "key-001",
            merchantId: String = "merchant-001",
            status: ApiKeyStatus = ApiKeyStatus.ACTIVE,
            environment: ApiKeyEnvironment = ApiKeyEnvironment.SANDBOX,
            revokedAt: Instant? = null,
        ) = MerchantApiKey(
            keyId = keyId,
            merchantId = merchantId,
            keyHash = "hash-$keyId",
            keyPrefix = "sk_test_ab12",
            environment = environment,
            status = status,
            scopes = setOf(ApiKeyScope.PAYMENT_WRITE, ApiKeyScope.PAYMENT_READ),
            description = null,
            expiredAt = null,
            graceExpiredAt = null,
            revokedAt = revokedAt,
            createdAt = Instant.now(),
            lastUsedAt = null
        )
    }
}
