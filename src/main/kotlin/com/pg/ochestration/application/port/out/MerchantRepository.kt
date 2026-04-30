package com.pg.ochestration.application.port.out

import com.pg.ochestration.domain.model.Merchant

interface MerchantRepository {
    fun save(merchant: Merchant): Merchant
    fun findById(merchantId: String): Merchant?
    fun findByEmail(email: String): Merchant?
    fun existsByEmail(email: String): Boolean
}
