package com.pg.ochestration.application.port.out

interface WebhookUrlValidator {
    fun validate(url: String): WebhookUrlValidationResult
}

data class WebhookUrlValidationResult(
    val allowed: Boolean,
    val reason: String? = null
) {
    companion object {
        fun allowed(): WebhookUrlValidationResult = WebhookUrlValidationResult(allowed = true)
        fun blocked(reason: String): WebhookUrlValidationResult = WebhookUrlValidationResult(allowed = false, reason = reason)
    }
}
