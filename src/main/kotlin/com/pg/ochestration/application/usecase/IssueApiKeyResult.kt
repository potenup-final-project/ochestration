package com.pg.ochestration.application.usecase

import com.pg.ochestration.domain.model.MerchantApiKey

data class IssueApiKeyResult(
    val apiKey: MerchantApiKey,
    val rawKey: String
)
