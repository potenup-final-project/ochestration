package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.application.usecase.IssueApiKeyCommand
import com.pg.ochestration.application.usecase.IssueApiKeyResult
import com.pg.ochestration.application.usecase.IssueApiKeyUseCase
import com.pg.ochestration.application.usecase.RevokeApiKeyUseCase
import com.pg.ochestration.domain.exception.InvalidApiKeyStateException
import com.pg.ochestration.domain.exception.MissingApiKeyException
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.ApiKeyScope
import com.pg.ochestration.domain.model.ApiKeyStatus
import com.pg.ochestration.domain.model.MerchantApiKey
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
    // POST /api/auth/keys — 발급
    // -------------------------------------------------------------------------

    @Test
    fun `should return 201 with rawKey when issueKey succeeds`() {
        val request = IssueApiKeyRequest(
            merchantId = "merchant-001",
            environment = ApiKeyEnvironment.SANDBOX,
            description = null
        )
        val issuedKey = anApiKey(status = ApiKeyStatus.ACTIVE)
        val result = IssueApiKeyResult(apiKey = issuedKey, rawKey = RAW_KEY)
        `when`(issueUseCase.issue(request.toCommand())).thenReturn(result)

        val response = controller.issueKey(request)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        assertEquals(RAW_KEY, response.body!!.rawKey)
        assertEquals(issuedKey.keyId, response.body!!.keyId)
        assertEquals(ApiKeyStatus.ACTIVE, response.body!!.status)
    }

    @Test
    fun `should include keyPrefix and environment in response when issueKey succeeds`() {
        val request = IssueApiKeyRequest(
            merchantId = "merchant-001",
            environment = ApiKeyEnvironment.SANDBOX,
            description = "테스트 Key"
        )
        val issuedKey = anApiKey(environment = ApiKeyEnvironment.SANDBOX)
        val result = IssueApiKeyResult(apiKey = issuedKey, rawKey = RAW_KEY)
        `when`(issueUseCase.issue(request.toCommand())).thenReturn(result)

        val response = controller.issueKey(request)

        assertEquals(issuedKey.keyPrefix, response.body!!.keyPrefix)
        assertEquals(ApiKeyEnvironment.SANDBOX, response.body!!.environment)
    }

    @Test
    fun `should propagate exception from IssueApiKeyUseCase when issue fails`() {
        val request = IssueApiKeyRequest(
            merchantId = "merchant-001",
            environment = ApiKeyEnvironment.SANDBOX,
            description = null
        )
        `when`(issueUseCase.issue(request.toCommand())).thenThrow(RuntimeException("발급 실패"))

        assertFailsWith<RuntimeException> {
            controller.issueKey(request)
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/auth/keys/{keyId} — 폐기
    // -------------------------------------------------------------------------

    @Test
    fun `should return REVOKED key response when valid X-Api-Key header is present`() {
        val revokedKey = anApiKey(keyId = "key-001", status = ApiKeyStatus.REVOKED, revokedAt = Instant.now())
        val httpRequest = MockHttpServletRequest().apply { addHeader("X-Api-Key", RAW_KEY) }
        `when`(revokeUseCase.revoke(keyId = "key-001", rawKey = RAW_KEY)).thenReturn(revokedKey)

        val response = controller.revokeKey(keyId = "key-001", httpRequest = httpRequest)

        assertEquals("key-001", response.keyId)
        assertEquals(ApiKeyStatus.REVOKED, response.status)
        assertNotNull(response.revokedAt)
    }

    @Test
    fun `should throw MissingApiKeyException when X-Api-Key header is absent`() {
        val httpRequest = MockHttpServletRequest()

        assertFailsWith<MissingApiKeyException> {
            controller.revokeKey(keyId = "key-001", httpRequest = httpRequest)
        }
    }

    @Test
    fun `should propagate InvalidApiKeyStateException when key is already REVOKED`() {
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
