package com.pg.ochestration.presentation.web.controller.response

import com.pg.ochestration.application.usecase.IssueApiKeyResult
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.ApiKeyStatus
import java.time.Instant

data class IssueApiKeyResponse(
    val keyId: String,
    val rawKey: String,
    val keyPrefix: String,
    val environment: ApiKeyEnvironment,
    val status: ApiKeyStatus,
    val createdAt: Instant,
    val expiresAt: Instant?
) {
    companion object {
        fun from(result: IssueApiKeyResult): IssueApiKeyResponse = IssueApiKeyResponse(
            keyId = result.apiKey.keyId,
            rawKey = result.rawKey,
            keyPrefix = result.apiKey.keyPrefix,
            environment = result.apiKey.environment,
            status = result.apiKey.status,
            createdAt = result.apiKey.createdAt,
            expiresAt = result.apiKey.expiredAt
        )
    }
}
