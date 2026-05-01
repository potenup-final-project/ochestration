package com.pg.ochestration.application.usecase

import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.domain.exception.MerchantNotFoundException
import com.pg.ochestration.domain.model.MerchantStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class MerchantStatusResult(
    val merchantId: String,
    val email: String,
    val status: MerchantStatus,
    val businessName: String?
)

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
