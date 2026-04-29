package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.domain.exception.AuthException
import com.pg.ochestration.domain.exception.EnvironmentMismatchException
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
            ApiErrorResponse(
                errorCode = "NOT_FOUND",
                message = ex.message ?: "Not found"
            )
        )

    @ExceptionHandler(IllegalStateException::class)
    fun handleInvalidState(ex: IllegalStateException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiErrorResponse(
                errorCode = "INVALID_STATE",
                message = ex.message ?: "Bad request"
            )
        )

    @ExceptionHandler(AuthException::class)
    fun handleAuthException(ex: AuthException): ResponseEntity<ApiErrorResponse> {
        val status = when (ex) {
            is EnvironmentMismatchException -> HttpStatus.FORBIDDEN
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
}
