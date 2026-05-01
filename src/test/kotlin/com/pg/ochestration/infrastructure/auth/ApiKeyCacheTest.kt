package com.pg.ochestration.infrastructure.auth

import com.pg.ochestration.domain.model.ApiKeyEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ApiKeyCacheTest {

    private val cache = ApiKeyCache(ttlSeconds = 60L)
    private val principal = MerchantPrincipal(merchantId = "merchant-001", environment = ApiKeyEnvironment.SANDBOX)

    @Test
    fun `should return principal when get is called within TTL`() {
        cache.put(KEY_HASH, principal)

        assertNotNull(cache.get(KEY_HASH))
        assertEquals(principal, cache.get(KEY_HASH))
    }

    @Test
    fun `should return null after evict is called`() {
        cache.put(KEY_HASH, principal)
        cache.evict(KEY_HASH)

        assertNull(cache.get(KEY_HASH))
    }

    @Test
    fun `should return null for key that was never put`() {
        assertNull(cache.get("non-existent-hash"))
    }

    @Test
    fun `should manage different keyHashes independently`() {
        val anotherPrincipal = MerchantPrincipal(merchantId = "merchant-002", environment = ApiKeyEnvironment.LIVE)
        val anotherHash = "another-hash"

        cache.put(KEY_HASH, principal)
        cache.put(anotherHash, anotherPrincipal)
        cache.evict(KEY_HASH)

        assertNull(cache.get(KEY_HASH))
        assertNotNull(cache.get(anotherHash))
        assertEquals(anotherPrincipal, cache.get(anotherHash))
    }

    private companion object {
        const val KEY_HASH = "abc123hash"
    }
}
