package com.pg.ochestration.presentation.web.controller.response

import com.pg.ochestration.application.usecase.result.ApproveLiveUpgradeResult
import com.pg.ochestration.domain.model.MerchantStatus

data class ApproveLiveResponse(
    val merchantId: String,
    val status: MerchantStatus,
    val liveKeyId: String
) {
    companion object {
        fun from(result: ApproveLiveUpgradeResult): ApproveLiveResponse = ApproveLiveResponse(
            merchantId = result.merchantId,
            status = result.status,
            liveKeyId = result.liveKeyId
        )
    }
}
