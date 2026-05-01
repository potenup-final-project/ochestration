package com.pg.ochestration.application.port.out

interface ApiKeyCachePort {
    fun evict(keyHash: String)
}
