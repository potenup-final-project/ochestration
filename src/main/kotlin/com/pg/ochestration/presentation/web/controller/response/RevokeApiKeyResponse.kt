package com.pg.ochestration.presentation.web.controller.response

import com.pg.ochestration.domain.model.ApiKeyStatus
import com.pg.ochestration.domain.model.MerchantApiKey
import java.time.Instant

data class RevokeApiKeyResponse(
    val keyId: String,
    val status: ApiKeyStatus,
    val revokedAt: Instant
) {
    companion object {
        fun from(apiKey: MerchantApiKey): RevokeApiKeyResponse = RevokeApiKeyResponse(
            keyId = apiKey.keyId,
            status = apiKey.status,
            revokedAt = requireNotNull(apiKey.revokedAt) {
                "폐기된 API Key에 revokedAt이 없습니다: ${apiKey.keyId}"
            }
        )
    }
}
