package com.pg.ochestration.infrastructure.auth

import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.infrastructure.auth.MerchantContext.merchantPrincipal
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MerchantContextTest {

    @Test
    fun `should return MerchantPrincipal when attribute is set on request`() {
        val request = MockHttpServletRequest()
        val principal = MerchantPrincipal(merchantId = "merchant-001", environment = ApiKeyEnvironment.SANDBOX)
        request.setAttribute(MerchantContext.ATTR_KEY, principal)

        val result = request.merchantPrincipal()

        assertEquals(principal, result)
    }

    @Test
    fun `should throw IllegalStateException when attribute is not set on request`() {
        val request = MockHttpServletRequest()

        assertFailsWith<IllegalStateException> {
            request.merchantPrincipal()
        }
    }
}
