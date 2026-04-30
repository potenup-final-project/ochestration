package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.application.usecase.ApproveLiveUpgradeResult
import com.pg.ochestration.application.usecase.ApproveLiveUpgradeUseCase
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import com.pg.ochestration.domain.model.MerchantStatus
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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

@Profile("!prod")
@RestController
@RequestMapping("/api/admin/merchants")
class AdminMerchantController(
    private val approveLiveUpgradeUseCase: ApproveLiveUpgradeUseCase
) {
    @PostMapping("/{merchantId}/approve-live")
    fun approveLive(
        @PathVariable merchantId: String,
        principal: MerchantPrincipal
    ): ApproveLiveResponse = ApproveLiveResponse.from(approveLiveUpgradeUseCase.approve(merchantId))
}
