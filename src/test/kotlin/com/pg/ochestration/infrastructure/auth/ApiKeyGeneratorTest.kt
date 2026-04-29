package com.pg.ochestration.infrastructure.auth

import com.pg.ochestration.domain.model.ApiKeyEnvironment
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ApiKeyGeneratorTest {

    private val generator = ApiKeyGenerator()

    @Test
    fun `should generate key with sk_test_ prefix for SANDBOX environment`() {
        val key = generator.generate(ApiKeyEnvironment.SANDBOX)

        assertTrue(key.startsWith("sk_test_"))
    }

    @Test
    fun `should generate key with sk_live_ prefix for LIVE environment`() {
        val key = generator.generate(ApiKeyEnvironment.LIVE)

        assertTrue(key.startsWith("sk_live_"))
    }

    @Test
    fun `should generate key with length of at least 40 characters`() {
        val sandboxKey = generator.generate(ApiKeyEnvironment.SANDBOX)
        val liveKey = generator.generate(ApiKeyEnvironment.LIVE)

        assertTrue(sandboxKey.length >= 40)
        assertTrue(liveKey.length >= 40)
    }

    @Test
    fun `should generate different keys on consecutive calls with same environment`() {
        val first = generator.generate(ApiKeyEnvironment.SANDBOX)
        val second = generator.generate(ApiKeyEnvironment.SANDBOX)

        assertNotEquals(first, second)
    }
}
