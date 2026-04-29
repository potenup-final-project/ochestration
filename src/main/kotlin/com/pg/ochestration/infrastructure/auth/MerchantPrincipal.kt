package com.pg.ochestration.infrastructure.auth

import com.pg.ochestration.domain.model.ApiKeyEnvironment
import kotlin.coroutines.CoroutineContext

data class MerchantPrincipal(
    val merchantId: String,
    val environment: ApiKeyEnvironment
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<MerchantPrincipal>

    override val key: CoroutineContext.Key<*> = Key
}
