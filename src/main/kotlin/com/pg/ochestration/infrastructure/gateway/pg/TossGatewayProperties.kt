package com.pg.ochestration.infrastructure.gateway.pg

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gateway.toss-test")
data class TossGatewayProperties(
    val baseUrl: String = "https://api.tosspayments.com",
    val secretKey: String = "",
    val connectTimeoutMs: Long = 3_000,
    val readTimeoutMs: Long = 5_000
)
