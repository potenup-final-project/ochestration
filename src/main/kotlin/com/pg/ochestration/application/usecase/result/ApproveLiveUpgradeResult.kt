package com.pg.ochestration.application.usecase.result

import com.pg.ochestration.domain.model.MerchantStatus

data class ApproveLiveUpgradeResult(
    val merchantId: String,
    val status: MerchantStatus,
    val liveKeyId: String
)
