package com.pg.ochestration.infrastructure.auth

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class ApiKeyCache(
    @Value("\${auth.api-key.cache-ttl-seconds:60}") ttlSeconds: Long
) {
    private val store: Cache<String, MerchantPrincipal> = Caffeine.newBuilder()
        .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
        .build()

    fun get(keyHash: String): MerchantPrincipal? = store.getIfPresent(keyHash)

    fun put(keyHash: String, principal: MerchantPrincipal) = store.put(keyHash, principal)

    fun evict(keyHash: String) = store.invalidate(keyHash)
}
