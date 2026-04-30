package com.pg.ochestration.domain.model

import java.time.Instant

data class Merchant(
    val merchantId: String,
    val email: String,
    val passwordHash: String,
    val businessName: String?,
    val businessRegistrationNumber: String?,
    val businessRegistrationFileUrl: String?,
    val status: MerchantStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun applyEmailVerification(): Merchant =
        copy(status = MerchantStatus.SANDBOX_ACTIVE, updatedAt = Instant.now())

    fun applyLiveUpgradeRequest(
        businessRegistrationNumber: String,
        businessRegistrationFileUrl: String
    ): Merchant = copy(
        status = MerchantStatus.LIVE_PENDING,
        businessRegistrationNumber = businessRegistrationNumber,
        businessRegistrationFileUrl = businessRegistrationFileUrl,
        updatedAt = Instant.now()
    )

    fun applyLiveApproval(): Merchant =
        copy(status = MerchantStatus.LIVE_ACTIVE, updatedAt = Instant.now())

    fun ensureEligibleForLiveUpgrade(connectedProviderCount: Int) =
        status.ensureEligibleForLiveUpgrade(merchantId, connectedProviderCount)
}
