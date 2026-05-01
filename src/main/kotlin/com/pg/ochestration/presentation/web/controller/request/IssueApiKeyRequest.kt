package com.pg.ochestration.presentation.web.controller.request

import com.pg.ochestration.application.usecase.IssueApiKeyCommand
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import jakarta.validation.constraints.NotBlank

data class IssueApiKeyRequest(
    @field:NotBlank val merchantId: String, // TODO(F-02): 인증 컨텍스트에서 제거
    val environment: ApiKeyEnvironment,
    val description: String? = null
) {
    fun toCommand(): IssueApiKeyCommand =
        IssueApiKeyCommand(
            merchantId = merchantId,
            environment = environment,
            description = description
        )
}
