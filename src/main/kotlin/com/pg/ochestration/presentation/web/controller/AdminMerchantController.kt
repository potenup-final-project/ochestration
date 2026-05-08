package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.application.usecase.ApproveLiveUpgradeUseCase
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import com.pg.ochestration.presentation.web.controller.response.ApproveLiveResponse
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Profile("!prod")
@RestController
@RequestMapping("/api/admin/merchants")
class AdminMerchantController(
    private val approveLiveUpgradeUseCase: ApproveLiveUpgradeUseCase
) {
    companion object {
        // DataSeeder에서 시드되는 관리자 전용 merchantId
        const val ADMIN_MERCHANT_ID = "merchant-001"
    }

    @PostMapping("/{merchantId}/approve-live")
    fun approveLive(
        @PathVariable merchantId: String,
        principal: MerchantPrincipal
    ): ApproveLiveResponse {
        if (principal.merchantId != ADMIN_MERCHANT_ID) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다")
        }
        return ApproveLiveResponse.from(approveLiveUpgradeUseCase.approve(merchantId))
    }
}
