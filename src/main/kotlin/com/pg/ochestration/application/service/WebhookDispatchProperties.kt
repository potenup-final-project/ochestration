package com.pg.ochestration.application.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "webhook.dispatch")
data class WebhookDispatchProperties(
    val enabled: Boolean = true,
    val fixedDelayMs: Long = 10_000,
    val batchSize: Int = 50,
    val connectTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 10_000
) {
    init {
        require(fixedDelayMs > 0) { "웹훅 dispatch 주기는 1ms 이상이어야 합니다: fixedDelayMs=$fixedDelayMs" }
        require(batchSize > 0) { "웹훅 dispatch batchSize는 1 이상이어야 합니다: batchSize=$batchSize" }
        require(connectTimeoutMs <= Int.MAX_VALUE) {
            "웹훅 connect timeout은 ${Int.MAX_VALUE}ms 이하여야 합니다: connectTimeoutMs=$connectTimeoutMs"
        }
        require(connectTimeoutMs > 0) { "웹훅 connect timeout은 1ms 이상이어야 합니다: connectTimeoutMs=$connectTimeoutMs" }
        require(readTimeoutMs > 0) { "웹훅 read timeout은 1ms 이상이어야 합니다: readTimeoutMs=$readTimeoutMs" }
    }
}
