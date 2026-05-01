package com.pg.ochestration.application.usecase

import com.pg.ochestration.domain.model.ApiKeyEnvironment

data class IssueApiKeyCommand(
    val merchantId: String,
    val environment: ApiKeyEnvironment,
    val description: String?
)
