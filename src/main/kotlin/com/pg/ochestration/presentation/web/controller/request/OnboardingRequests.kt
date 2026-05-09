package com.pg.ochestration.presentation.web.controller.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class RegisterRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
    val businessName: String? = null
)

data class VerifyEmailRequest(
    @field:NotBlank val token: String
)

data class LiveUpgradeRequest(
    @field:NotBlank val businessRegistrationNumber: String,
    @field:NotBlank val businessRegistrationFileUrl: String
)
