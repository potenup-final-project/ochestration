package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.EnvironmentMismatchException
import com.pg.ochestration.domain.exception.ExpiredApiKeyException
import com.pg.ochestration.domain.exception.InvalidApiKeyStateException
import com.pg.ochestration.domain.exception.RevokedApiKeyException
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MerchantApiKeyTest {

    // -------------------------------------------------------------------------
    // ensureNotExpired
    // -------------------------------------------------------------------------

    @Test
    fun `should throw ExpiredApiKeyException when expiredAt is one second before now`() {
        val key = aKey(expiredAt = Instant.now().minusSeconds(1))
        assertFailsWith<ExpiredApiKeyException> { key.ensureNotExpired() }
    }

    @Test
    fun `should not throw when expiredAt is one second after now`() {
        val key = aKey(expiredAt = Instant.now().plusSeconds(1))
        key.ensureNotExpired()
    }

    @Test
    fun `should not throw when expiredAt is null`() {
        val key = aKey(expiredAt = null)
        key.ensureNotExpired()
    }

    // -------------------------------------------------------------------------
    // ensureNotRevoked
    // -------------------------------------------------------------------------

    @Test
    fun `should throw RevokedApiKeyException when status is REVOKED`() {
        val key = aKey(status = ApiKeyStatus.REVOKED)
        assertFailsWith<RevokedApiKeyException> { key.ensureNotRevoked() }
    }

    @Test
    fun `should not throw when status is ACTIVE on ensureNotRevoked`() {
        val key = aKey(status = ApiKeyStatus.ACTIVE)
        key.ensureNotRevoked()
    }

    // -------------------------------------------------------------------------
    // ensureRevokable
    // -------------------------------------------------------------------------

    @Test
    fun `should throw InvalidApiKeyStateException when status is REVOKED on ensureRevokable`() {
        val key = aKey(status = ApiKeyStatus.REVOKED)
        assertFailsWith<InvalidApiKeyStateException> { key.ensureRevokable() }
    }

    @Test
    fun `should throw InvalidApiKeyStateException when status is EXPIRED on ensureRevokable`() {
        val key = aKey(status = ApiKeyStatus.EXPIRED)
        assertFailsWith<InvalidApiKeyStateException> { key.ensureRevokable() }
    }

    @Test
    fun `should not throw when status is ACTIVE on ensureRevokable`() {
        val key = aKey(status = ApiKeyStatus.ACTIVE)
        key.ensureRevokable()
    }

    @Test
    fun `should not throw when status is GRACE_PERIOD on ensureRevokable`() {
        val key = aKey(status = ApiKeyStatus.GRACE_PERIOD)
        key.ensureRevokable()
    }

    // -------------------------------------------------------------------------
    // ensureEnvironmentMatches
    // -------------------------------------------------------------------------

    @Test
    fun `should throw EnvironmentMismatchException when key is SANDBOX but request is LIVE`() {
        val key = aKey(environment = ApiKeyEnvironment.SANDBOX)
        assertFailsWith<EnvironmentMismatchException> {
            key.ensureEnvironmentMatches(ApiKeyEnvironment.LIVE)
        }
    }

    @Test
    fun `should throw EnvironmentMismatchException when key is LIVE but request is SANDBOX`() {
        val key = aKey(environment = ApiKeyEnvironment.LIVE)
        assertFailsWith<EnvironmentMismatchException> {
            key.ensureEnvironmentMatches(ApiKeyEnvironment.SANDBOX)
        }
    }

    @Test
    fun `should not throw when key environment and request environment are both SANDBOX`() {
        val key = aKey(environment = ApiKeyEnvironment.SANDBOX)
        key.ensureEnvironmentMatches(ApiKeyEnvironment.SANDBOX)
    }

    @Test
    fun `should not throw when key environment and request environment are both LIVE`() {
        val key = aKey(environment = ApiKeyEnvironment.LIVE)
        key.ensureEnvironmentMatches(ApiKeyEnvironment.LIVE)
    }

    // -------------------------------------------------------------------------
    // revoke
    // -------------------------------------------------------------------------

    @Test
    fun `should return key with REVOKED status and revokedAt set when status is ACTIVE`() {
        val now = Instant.now()
        val key = aKey(status = ApiKeyStatus.ACTIVE)

        val revoked = key.revoke(now)

        assertEquals(ApiKeyStatus.REVOKED, revoked.status)
        assertNotNull(revoked.revokedAt)
        assertEquals(now, revoked.revokedAt)
    }

    @Test
    fun `should throw InvalidApiKeyStateException when revoking already REVOKED key`() {
        val key = aKey(status = ApiKeyStatus.REVOKED)
        assertFailsWith<InvalidApiKeyStateException> { key.revoke(Instant.now()) }
    }

    @Test
    fun `should throw InvalidApiKeyStateException when revoking EXPIRED key`() {
        val key = aKey(status = ApiKeyStatus.EXPIRED)
        assertFailsWith<InvalidApiKeyStateException> { key.revoke(Instant.now()) }
    }

    // -------------------------------------------------------------------------
    // isActive
    // -------------------------------------------------------------------------

    @Test
    fun `should return true when status is ACTIVE`() {
        val key = aKey(status = ApiKeyStatus.ACTIVE)
        assertTrue(key.isActive())
    }

    @Test
    fun `should return true when status is GRACE_PERIOD and graceExpiredAt is one hour from now`() {
        val key = aKey(
            status = ApiKeyStatus.GRACE_PERIOD,
            graceExpiredAt = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        assertTrue(key.isActive())
    }

    @Test
    fun `should return false when status is GRACE_PERIOD and graceExpiredAt is one second before now`() {
        val key = aKey(
            status = ApiKeyStatus.GRACE_PERIOD,
            graceExpiredAt = Instant.now().minusSeconds(1)
        )
        assertFalse(key.isActive())
    }

    @Test
    fun `should return false when status is GRACE_PERIOD and graceExpiredAt is null`() {
        val key = aKey(status = ApiKeyStatus.GRACE_PERIOD, graceExpiredAt = null)
        assertFalse(key.isActive())
    }

    @Test
    fun `should return false when status is REVOKED`() {
        val key = aKey(status = ApiKeyStatus.REVOKED)
        assertFalse(key.isActive())
    }

    @Test
    fun `should return false when status is EXPIRED`() {
        val key = aKey(status = ApiKeyStatus.EXPIRED)
        assertFalse(key.isActive())
    }
}

// -------------------------------------------------------------------------
// Fixture
// -------------------------------------------------------------------------

private fun aKey(
    keyId: String = "key-001",
    merchantId: String = "merchant-001",
    status: ApiKeyStatus = ApiKeyStatus.ACTIVE,
    environment: ApiKeyEnvironment = ApiKeyEnvironment.SANDBOX,
    expiredAt: Instant? = null,
    graceExpiredAt: Instant? = null,
    revokedAt: Instant? = null,
) = MerchantApiKey(
    keyId = keyId,
    merchantId = merchantId,
    keyHash = "hash-$keyId",
    keyPrefix = "sk_test_ab12",
    environment = environment,
    status = status,
    scopes = setOf(ApiKeyScope.PAYMENT_WRITE),
    description = null,
    expiredAt = expiredAt,
    graceExpiredAt = graceExpiredAt,
    revokedAt = revokedAt,
    createdAt = Instant.now(),
    lastUsedAt = null
)
