package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.MerchantNotEligibleForLiveException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MerchantStatusTest {

    @Test
    fun `LIVE_PENDING 상태에서 Live 전환 신청 시 MerchantNotEligibleForLiveException 발생`() {
        val exception = assertThrows<MerchantNotEligibleForLiveException> {
            MerchantStatus.LIVE_PENDING.ensureEligibleForLiveUpgrade("merchant-test", connectedProviderCount = 1)
        }
        assertEquals("MERCHANT_NOT_ELIGIBLE_FOR_LIVE", exception.errorCode)
    }

    @Test
    fun `LIVE_ACTIVE 상태에서 Live 전환 신청 시 MerchantNotEligibleForLiveException 발생`() {
        val exception = assertThrows<MerchantNotEligibleForLiveException> {
            MerchantStatus.LIVE_ACTIVE.ensureEligibleForLiveUpgrade("merchant-test", connectedProviderCount = 1)
        }
        assertEquals("MERCHANT_NOT_ELIGIBLE_FOR_LIVE", exception.errorCode)
    }

    @Test
    fun `PENDING 상태에서 Live 전환 신청 시 MerchantNotEligibleForLiveException 발생`() {
        val exception = assertThrows<MerchantNotEligibleForLiveException> {
            MerchantStatus.PENDING.ensureEligibleForLiveUpgrade("merchant-test", connectedProviderCount = 1)
        }
        assertEquals("MERCHANT_NOT_ELIGIBLE_FOR_LIVE", exception.errorCode)
    }

    @Test
    fun `SANDBOX_ACTIVE 상태에서 connectedProviderCount 0이면 MerchantNotEligibleForLiveException 발생`() {
        val exception = assertThrows<MerchantNotEligibleForLiveException> {
            MerchantStatus.SANDBOX_ACTIVE.ensureEligibleForLiveUpgrade("merchant-test", connectedProviderCount = 0)
        }
        assertEquals("MERCHANT_NOT_ELIGIBLE_FOR_LIVE", exception.errorCode)
    }

    @Test
    fun `SANDBOX_ACTIVE 상태에서 connectedProviderCount 1 이상이면 정상 통과`() {
        MerchantStatus.SANDBOX_ACTIVE.ensureEligibleForLiveUpgrade("merchant-test", connectedProviderCount = 1)
        MerchantStatus.SANDBOX_ACTIVE.ensureEligibleForLiveUpgrade("merchant-test", connectedProviderCount = 3)
    }

    @Test
    fun `isSandboxOnly는 PENDING과 SANDBOX_ACTIVE에서 true 반환`() {
        assert(MerchantStatus.PENDING.isSandboxOnly())
        assert(MerchantStatus.SANDBOX_ACTIVE.isSandboxOnly())
        assert(!MerchantStatus.LIVE_PENDING.isSandboxOnly())
        assert(!MerchantStatus.LIVE_ACTIVE.isSandboxOnly())
    }

    @Test
    fun `isLiveEligible는 LIVE_ACTIVE에서만 true 반환`() {
        assert(MerchantStatus.LIVE_ACTIVE.isLiveEligible())
        assert(!MerchantStatus.LIVE_PENDING.isLiveEligible())
        assert(!MerchantStatus.SANDBOX_ACTIVE.isLiveEligible())
        assert(!MerchantStatus.PENDING.isLiveEligible())
    }
}
