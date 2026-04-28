package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.InvalidApiKeyStateException
import com.pg.ochestration.domain.exception.RevokedApiKeyException

enum class ApiKeyStatus {
    ACTIVE,
    GRACE_PERIOD,
    REVOKED,
    EXPIRED;

    fun ensureNotRevoked(keyId: String) {
        if (this == REVOKED) throw RevokedApiKeyException(keyId)
    }

    fun ensureRevokable(keyId: String) {
        if (this == REVOKED || this == EXPIRED)
            throw InvalidApiKeyStateException(keyId, this)
    }

    fun isTerminal(): Boolean = this == REVOKED || this == EXPIRED

    fun isActive(): Boolean = this == ACTIVE || this == GRACE_PERIOD
}
