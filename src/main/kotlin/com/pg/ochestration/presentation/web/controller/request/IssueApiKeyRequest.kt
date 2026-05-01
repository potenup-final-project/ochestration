package com.pg.ochestration.presentation.web.controller.request

import com.pg.ochestration.application.usecase.IssueApiKeyCommand
import com.pg.ochestration.domain.model.ApiKeyEnvironment

data class IssueApiKeyRequest(
    val environment: ApiKeyEnvironment,
    val description: String? = null
) {
    fun toCommand(merchantId: String): IssueApiKeyCommand =
        IssueApiKeyCommand(
            merchantId = merchantId,
            environment = environment,
            description = description
        )
}
