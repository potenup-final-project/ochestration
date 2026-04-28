package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.InvalidApiKeyStateException
import com.pg.ochestration.domain.exception.RevokedApiKeyException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiKeyStatusTest {

    // -------------------------------------------------------------------------
    // ensureNotRevoked
    // -------------------------------------------------------------------------

    @Test
    fun `should throw RevokedApiKeyException when status is REVOKED`() {
        assertFailsWith<RevokedApiKeyException> {
            ApiKeyStatus.REVOKED.ensureNotRevoked("key-001")
        }
    }

    @Test
    fun `should not throw when status is ACTIVE on ensureNotRevoked`() {
        ApiKeyStatus.ACTIVE.ensureNotRevoked("key-001")
    }

    @Test
    fun `should not throw when status is GRACE_PERIOD on ensureNotRevoked`() {
        ApiKeyStatus.GRACE_PERIOD.ensureNotRevoked("key-001")
    }

    @Test
    fun `should not throw when status is EXPIRED on ensureNotRevoked`() {
        ApiKeyStatus.EXPIRED.ensureNotRevoked("key-001")
    }

    // -------------------------------------------------------------------------
    // ensureRevokable
    // -------------------------------------------------------------------------

    @Test
    fun `should throw InvalidApiKeyStateException when status is REVOKED on ensureRevokable`() {
        assertFailsWith<InvalidApiKeyStateException> {
            ApiKeyStatus.REVOKED.ensureRevokable("key-001")
        }
    }

    @Test
    fun `should throw InvalidApiKeyStateException when status is EXPIRED on ensureRevokable`() {
        assertFailsWith<InvalidApiKeyStateException> {
            ApiKeyStatus.EXPIRED.ensureRevokable("key-001")
        }
    }

    @Test
    fun `should not throw when status is ACTIVE on ensureRevokable`() {
        ApiKeyStatus.ACTIVE.ensureRevokable("key-001")
    }

    @Test
    fun `should not throw when status is GRACE_PERIOD on ensureRevokable`() {
        ApiKeyStatus.GRACE_PERIOD.ensureRevokable("key-001")
    }

    // -------------------------------------------------------------------------
    // isTerminal
    // -------------------------------------------------------------------------

    @Test
    fun `should return true when status is REVOKED on isTerminal`() {
        assertTrue(ApiKeyStatus.REVOKED.isTerminal())
    }

    @Test
    fun `should return true when status is EXPIRED on isTerminal`() {
        assertTrue(ApiKeyStatus.EXPIRED.isTerminal())
    }

    @Test
    fun `should return false when status is ACTIVE on isTerminal`() {
        assertFalse(ApiKeyStatus.ACTIVE.isTerminal())
    }

    @Test
    fun `should return false when status is GRACE_PERIOD on isTerminal`() {
        assertFalse(ApiKeyStatus.GRACE_PERIOD.isTerminal())
    }

    // -------------------------------------------------------------------------
    // isActive
    // -------------------------------------------------------------------------

    @Test
    fun `should return true when status is ACTIVE on isActive`() {
        assertTrue(ApiKeyStatus.ACTIVE.isActive())
    }

    @Test
    fun `should return true when status is GRACE_PERIOD on isActive`() {
        assertTrue(ApiKeyStatus.GRACE_PERIOD.isActive())
    }

    @Test
    fun `should return false when status is REVOKED on isActive`() {
        assertFalse(ApiKeyStatus.REVOKED.isActive())
    }

    @Test
    fun `should return false when status is EXPIRED on isActive`() {
        assertFalse(ApiKeyStatus.EXPIRED.isActive())
    }
}
