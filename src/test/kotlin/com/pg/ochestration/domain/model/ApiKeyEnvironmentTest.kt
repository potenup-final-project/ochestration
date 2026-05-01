package com.pg.ochestration.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiKeyEnvironmentTest {

    @Test
    fun `should return SANDBOX when key starts with sk_test_ prefix`() {
        assertEquals(ApiKeyEnvironment.SANDBOX, ApiKeyEnvironment.fromPrefix("sk_test_abc123"))
    }

    @Test
    fun `should return LIVE when key starts with sk_live_ prefix`() {
        assertEquals(ApiKeyEnvironment.LIVE, ApiKeyEnvironment.fromPrefix("sk_live_abc123"))
    }

    @Test
    fun `should return SANDBOX when key is exactly the sk_test_ prefix boundary`() {
        assertEquals(ApiKeyEnvironment.SANDBOX, ApiKeyEnvironment.fromPrefix("sk_test_"))
    }

    @Test
    fun `should return LIVE when key is exactly the sk_live_ prefix boundary`() {
        assertEquals(ApiKeyEnvironment.LIVE, ApiKeyEnvironment.fromPrefix("sk_live_"))
    }

    @Test
    fun `should return LIVE when key does not match any known prefix`() {
        assertEquals(ApiKeyEnvironment.LIVE, ApiKeyEnvironment.fromPrefix("other_key"))
    }
}
