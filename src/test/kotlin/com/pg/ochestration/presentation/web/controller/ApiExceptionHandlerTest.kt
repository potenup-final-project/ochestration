package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.domain.exception.EnvironmentMismatchException
import com.pg.ochestration.domain.exception.ExpiredApiKeyException
import com.pg.ochestration.domain.exception.InvalidApiKeyException
import com.pg.ochestration.domain.exception.InvalidApiKeyStateException
import com.pg.ochestration.domain.exception.MissingApiKeyException
import com.pg.ochestration.domain.exception.PaymentAccessDeniedException
import com.pg.ochestration.domain.exception.RevokedApiKeyException
import com.pg.ochestration.domain.exception.WebhookEndpointDescriptionTooLongException
import com.pg.ochestration.domain.exception.WebhookEndpointLimitExceededException
import com.pg.ochestration.domain.exception.WebhookEndpointNotFoundException
import com.pg.ochestration.domain.exception.WebhookEndpointUrlNotAllowedException
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.ApiKeyStatus
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApiExceptionHandlerTest {

    private val handler = ApiExceptionHandler()

    @Test
    fun `should return 401 with MISSING_API_KEY errorCode for MissingApiKeyException`() {
        val response = handler.handleAuthException(MissingApiKeyException())

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("MISSING_API_KEY", response.body?.errorCode)
    }

    @Test
    fun `should return 401 with INVALID_API_KEY errorCode for InvalidApiKeyException`() {
        val response = handler.handleAuthException(InvalidApiKeyException())

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("INVALID_API_KEY", response.body?.errorCode)
    }

    @Test
    fun `should return 401 with API_KEY_EXPIRED errorCode for ExpiredApiKeyException`() {
        val response = handler.handleAuthException(ExpiredApiKeyException("key-001"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("API_KEY_EXPIRED", response.body?.errorCode)
    }

    @Test
    fun `should return 401 with API_KEY_REVOKED errorCode for RevokedApiKeyException`() {
        val response = handler.handleAuthException(RevokedApiKeyException("key-001"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("API_KEY_REVOKED", response.body?.errorCode)
    }

    @Test
    fun `should return 403 with ENVIRONMENT_MISMATCH errorCode for EnvironmentMismatchException`() {
        val exception = EnvironmentMismatchException(
            keyEnv = ApiKeyEnvironment.SANDBOX,
            requestEnv = ApiKeyEnvironment.LIVE
        )

        val response = handler.handleAuthException(exception)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("ENVIRONMENT_MISMATCH", response.body?.errorCode)
    }

    @Test
    fun `should return 404 with NOT_FOUND errorCode for IllegalArgumentException`() {
        val response = handler.handleNotFound(IllegalArgumentException("resource not found"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("NOT_FOUND", response.body?.errorCode)
        assertEquals("resource not found", response.body?.message)
    }

    @Test
    fun `should return 400 with INVALID_STATE errorCode for IllegalStateException`() {
        val response = handler.handleInvalidState(IllegalStateException("invalid state transition"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_STATE", response.body?.errorCode)
        assertEquals("invalid state transition", response.body?.message)
    }

    @Test
    fun `should return 403 with PAYMENT_ACCESS_DENIED errorCode for PaymentAccessDeniedException`() {
        val exception = PaymentAccessDeniedException(
            paymentId = "payment-001",
            merchantId = "merchant-002"
        )

        val response = handler.handleAuthException(exception)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("PAYMENT_ACCESS_DENIED", response.body?.errorCode)
    }

    @Test
    fun `should return 409 with INVALID_API_KEY_STATE errorCode for InvalidApiKeyStateException`() {
        val exception = InvalidApiKeyStateException("key-001", ApiKeyStatus.REVOKED)

        val response = handler.handleAuthException(exception)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("INVALID_API_KEY_STATE", response.body?.errorCode)
    }

    @Test
    fun `should include timestamp in auth error response body`() {
        val response = handler.handleAuthException(MissingApiKeyException())

        assertNotNull(response.body?.timestamp)
    }

    @Test
    fun `should return 404 with WEBHOOK_ENDPOINT_NOT_FOUND for WebhookEndpointNotFoundException`() {
        val response = handler.handleWebhookException(WebhookEndpointNotFoundException("endpoint-001"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("WEBHOOK_ENDPOINT_NOT_FOUND", response.body?.errorCode)
    }

    @Test
    fun `should return 409 with WEBHOOK_ENDPOINT_LIMIT_EXCEEDED for WebhookEndpointLimitExceededException`() {
        val response = handler.handleWebhookException(WebhookEndpointLimitExceededException("merchant-001", 5))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("WEBHOOK_ENDPOINT_LIMIT_EXCEEDED", response.body?.errorCode)
    }

    @Test
    fun `should return 400 with WEBHOOK_ENDPOINT_URL_NOT_ALLOWED for WebhookEndpointUrlNotAllowedException`() {
        val response = handler.handleWebhookException(WebhookEndpointUrlNotAllowedException("localhost"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("WEBHOOK_ENDPOINT_URL_NOT_ALLOWED", response.body?.errorCode)
    }

    @Test
    fun `should return 400 with WEBHOOK_ENDPOINT_DESCRIPTION_TOO_LONG for WebhookEndpointDescriptionTooLongException`() {
        val response = handler.handleWebhookException(WebhookEndpointDescriptionTooLongException(256, 255))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("WEBHOOK_ENDPOINT_DESCRIPTION_TOO_LONG", response.body?.errorCode)
    }

}
