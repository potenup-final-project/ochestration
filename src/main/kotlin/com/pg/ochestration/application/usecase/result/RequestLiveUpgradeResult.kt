package com.pg.ochestration.application.usecase.result

import com.pg.ochestration.domain.model.MerchantStatus

data class RequestLiveUpgradeResult(
    val merchantId: String,
    val status: MerchantStatus
)
