package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.presentation.web.dto.ApiErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleNotFound(ex: IllegalArgumentException): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse(ex.message ?: "Not found"))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleInvalidState(ex: IllegalStateException): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse(ex.message ?: "Bad request"))
    }
}
