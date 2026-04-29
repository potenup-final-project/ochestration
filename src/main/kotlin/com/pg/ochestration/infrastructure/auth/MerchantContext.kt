package com.pg.ochestration.infrastructure.auth

import jakarta.servlet.http.HttpServletRequest

object MerchantContext {

    const val ATTR_KEY = "MERCHANT_PRINCIPAL"

    fun HttpServletRequest.merchantPrincipal(): MerchantPrincipal =
        (getAttribute(ATTR_KEY) as? MerchantPrincipal)
            ?: error(
                "MerchantPrincipal not found in request attributes. " +
                "Ensure ApiKeyAuthInterceptor is registered and auth.api-key.enabled=true."
            )
}
