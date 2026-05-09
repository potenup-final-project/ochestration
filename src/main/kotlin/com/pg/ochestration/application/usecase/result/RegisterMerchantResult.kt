package com.pg.ochestration.application.usecase.result

import com.pg.ochestration.domain.model.MerchantStatus

data class RegisterMerchantResult(
    val merchantId: String,
    val email: String,
    val status: MerchantStatus
)
