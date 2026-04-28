package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.EnvironmentMismatchException
import com.pg.ochestration.domain.exception.ExpiredApiKeyException
import java.time.Instant

data class MerchantApiKey(
    val keyId: String,
    val merchantId: String,
    val keyHash: String,
    val keyPrefix: String,
    val environment: ApiKeyEnvironment,
    val status: ApiKeyStatus,
    val scopes: Set<ApiKeyScope>,
    val description: String?,
    val expiredAt: Instant?,
    val graceExpiredAt: Instant?,
    val revokedAt: Instant?,
    val createdAt: Instant,
    val lastUsedAt: Instant?
) {
    fun ensureNotExpired() {
        if (expiredAt != null && expiredAt.isBefore(Instant.now()))
            throw ExpiredApiKeyException(keyId)
    }

    fun ensureNotRevoked() = status.ensureNotRevoked(keyId)

    fun ensureRevokable() = status.ensureRevokable(keyId)

    fun ensureEnvironmentMatches(requestEnvironment: ApiKeyEnvironment) {
        if (this.environment != requestEnvironment)
            throw EnvironmentMismatchException(this.environment, requestEnvironment)
    }

    fun revoke(now: Instant): MerchantApiKey {
        status.ensureRevokable(keyId)
        return copy(status = ApiKeyStatus.REVOKED, revokedAt = now)
    }

    fun isActive(): Boolean =
        status == ApiKeyStatus.ACTIVE || isGracePeriodStillValid()

    private fun isGracePeriodStillValid(): Boolean =
        status == ApiKeyStatus.GRACE_PERIOD &&
        graceExpiredAt != null &&
        graceExpiredAt.isAfter(Instant.now())
}
