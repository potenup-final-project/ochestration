package com.pg.ochestration.presentation.web.controller.response

import java.time.Instant

data class ApiErrorResponse(
    val errorCode: String,
    val message: String,
    val timestamp: Instant = Instant.now()
)
