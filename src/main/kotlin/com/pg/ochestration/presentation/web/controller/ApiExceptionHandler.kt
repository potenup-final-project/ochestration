package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.domain.exception.AuthException
import com.pg.ochestration.domain.exception.DuplicateEmailException
import com.pg.ochestration.domain.exception.EnvironmentMismatchException
import com.pg.ochestration.domain.exception.EnvironmentMismatchForOnboardingException
import com.pg.ochestration.domain.exception.InvalidApiKeyStateException
import com.pg.ochestration.domain.exception.InvalidEmailTokenException
import com.pg.ochestration.domain.exception.InvalidOnboardingTokenException
import com.pg.ochestration.domain.exception.MerchantNotEligibleForLiveException
import com.pg.ochestration.domain.exception.MerchantNotFoundException
import com.pg.ochestration.domain.exception.OnboardingException
import com.pg.ochestration.domain.exception.OnboardingTokenExpiredException
import com.pg.ochestration.domain.exception.PaymentAccessDeniedException
import com.pg.ochestration.domain.exception.PaymentException
import com.pg.ochestration.domain.exception.PaymentNotFoundException
import com.pg.ochestration.domain.exception.PaymentNotCancelableException
import com.pg.ochestration.domain.exception.PaymentNotFailableException
import com.pg.ochestration.presentation.web.dto.ApiErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleNotFound(ex: IllegalArgumentException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse(errorCode = "NOT_FOUND", message = ex.message ?: "Not found")
        )

    @ExceptionHandler(IllegalStateException::class)
    fun handleInvalidState(ex: IllegalStateException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiErrorResponse(errorCode = "INVALID_STATE", message = ex.message ?: "Bad request")
        )

    @ExceptionHandler(AuthException::class)
    fun handleAuthException(ex: AuthException): ResponseEntity<ApiErrorResponse> {
        val status = when (ex) {
            is EnvironmentMismatchException -> HttpStatus.FORBIDDEN
            is PaymentAccessDeniedException -> HttpStatus.FORBIDDEN
            is InvalidApiKeyStateException -> HttpStatus.CONFLICT
            else -> HttpStatus.UNAUTHORIZED
        }
        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                errorCode = ex.errorCode,
                message = ex.message ?: "Authentication failed",
                timestamp = Instant.now()
            )
        )
    }

    @ExceptionHandler(OnboardingException::class)
    fun handleOnboardingException(ex: OnboardingException): ResponseEntity<ApiErrorResponse> {
        val status = when (ex) {
            is DuplicateEmailException -> HttpStatus.CONFLICT
            is MerchantNotEligibleForLiveException -> HttpStatus.CONFLICT
            is MerchantNotFoundException -> HttpStatus.NOT_FOUND
            is EnvironmentMismatchForOnboardingException -> HttpStatus.FORBIDDEN
            is InvalidEmailTokenException -> HttpStatus.BAD_REQUEST
            is InvalidOnboardingTokenException -> HttpStatus.BAD_REQUEST
            is OnboardingTokenExpiredException -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                errorCode = ex.errorCode,
                message = ex.message ?: "Onboarding error",
                timestamp = Instant.now()
            )
        )
    }

    @ExceptionHandler(PaymentException::class)
    fun handlePaymentException(ex: PaymentException): ResponseEntity<ApiErrorResponse> {
        val status = when (ex) {
            is PaymentNotFoundException -> HttpStatus.NOT_FOUND
            is PaymentNotCancelableException -> HttpStatus.CONFLICT
            is PaymentNotFailableException -> HttpStatus.CONFLICT
        }
        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                errorCode = ex.errorCode,
                message = ex.message ?: "Payment error",
                timestamp = Instant.now()
            )
        )
    }
}
