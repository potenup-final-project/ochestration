package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.MerchantNotEligibleForLiveException

enum class MerchantStatus {
    PENDING,
    SANDBOX_ACTIVE,
    LIVE_PENDING,
    LIVE_ACTIVE;

    fun ensureEligibleForLiveUpgrade(merchantId: String, connectedProviderCount: Int) {
        if (this != SANDBOX_ACTIVE)
            throw MerchantNotEligibleForLiveException(
                merchantId = merchantId,
                status = this,
                reason = "SANDBOX_ACTIVE 상태에서만 Live 전환 신청이 가능합니다"
            )
        if (connectedProviderCount < 1)
            throw MerchantNotEligibleForLiveException(
                merchantId = merchantId,
                status = this,
                reason = "최소 1개의 PG 연결이 필요합니다"
            )
    }

    fun isSandboxOnly(): Boolean = this == PENDING || this == SANDBOX_ACTIVE

    fun isLiveEligible(): Boolean = this == LIVE_ACTIVE
}
