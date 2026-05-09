package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.application.usecase.result.MerchantStatusResult
import com.pg.ochestration.domain.exception.MerchantNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GetMerchantStatusUseCase(
    private val merchantRepository: MerchantRepository
) {
    fun get(merchantId: String): MerchantStatusResult {
        val merchant = merchantRepository.findById(merchantId)
            ?: throw MerchantNotFoundException(merchantId)

        return MerchantStatusResult(
            merchantId = merchant.merchantId,
            email = merchant.email,
            status = merchant.status,
            businessName = merchant.businessName
        )
    }
}
