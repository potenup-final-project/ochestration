package com.pg.ochestration.domain.model

enum class ApiKeyEnvironment {
    SANDBOX, LIVE;

    companion object {
        fun fromPrefix(rawKey: String): ApiKeyEnvironment =
            if (rawKey.startsWith("sk_test_")) SANDBOX else LIVE
    }
}
