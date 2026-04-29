package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.application.usecase.IssueApiKeyUseCase
import com.pg.ochestration.application.usecase.RevokeApiKeyUseCase
import com.pg.ochestration.domain.exception.MissingApiKeyException
import com.pg.ochestration.presentation.web.controller.request.IssueApiKeyRequest
import com.pg.ochestration.presentation.web.controller.response.IssueApiKeyResponse
import com.pg.ochestration.presentation.web.controller.response.RevokeApiKeyResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("!prod")
@RestController
@RequestMapping("/api/auth/keys")
class ApiKeyController(
    private val issueApiKeyUseCase: IssueApiKeyUseCase,
    private val revokeApiKeyUseCase: RevokeApiKeyUseCase
) {
    @PostMapping
    fun issueKey(@Valid @RequestBody request: IssueApiKeyRequest): ResponseEntity<IssueApiKeyResponse> {
        val result = issueApiKeyUseCase.issue(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(IssueApiKeyResponse.from(result))
    }

    @DeleteMapping("/{keyId}")
    fun revokeKey(
        @PathVariable keyId: String,
        httpRequest: HttpServletRequest
    ): RevokeApiKeyResponse {
        val rawKey = httpRequest.getHeader("X-Api-Key")
            ?: throw MissingApiKeyException()
        val revoked = revokeApiKeyUseCase.revoke(keyId = keyId, rawKey = rawKey)
        return RevokeApiKeyResponse.from(revoked)
    }
}
