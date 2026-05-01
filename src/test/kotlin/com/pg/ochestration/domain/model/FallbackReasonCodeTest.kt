package com.pg.ochestration.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FallbackReasonCodeTest {

    @Test
    fun `FallbackReasonCode 모든 항목은 SCREAMING_SNAKE_CASE 네이밍을 따른다`() {
        val screaming = Regex("^[A-Z][A-Z0-9_]*$")
        FallbackReasonCode.entries.forEach { code ->
            assertEquals(
                true,
                screaming.matches(code.name),
                "FallbackReasonCode.${code.name} 이 SCREAMING_SNAKE_CASE를 따르지 않습니다"
            )
        }
    }

    @Test
    fun `SelectionSummary noProvider factory는 NO_PROVIDER_AVAILABLE fallbackReasonCode를 반환한다`() {
        val summary = SelectionSummary.noProvider(
            initialCandidates = listOf(Provider.TOSS),
            filteredOutProviders = emptyList()
        )
        assertEquals(FallbackReasonCode.NO_PROVIDER_AVAILABLE, summary.fallbackReasonCode)
    }

    @Test
    fun `SelectionSummary exhausted factory는 지정된 fallbackReasonCode를 반환한다`() {
        val selection = ProviderSelectionResult(
            initialCandidates = listOf(Provider.TOSS, Provider.KAKAOPAY),
            filteredOutProviders = emptyList(),
            candidates = listOf(Provider.TOSS, Provider.KAKAOPAY),
            selectedPrimaryProvider = Provider.TOSS,
            selectedPrimaryReason = SelectionPrimaryReason.HIGHEST_PRIORITY_DEFAULT
        )

        val summary = SelectionSummary.exhausted(
            selection = selection,
            fallbackReasonCode = FallbackReasonCode.ALL_PROVIDERS_EXHAUSTED
        )

        assertEquals(FallbackReasonCode.ALL_PROVIDERS_EXHAUSTED, summary.fallbackReasonCode)
    }

    @Test
    fun `SelectionSummary approved factory는 전달된 fallbackReasonCode를 그대로 보존한다`() {
        val selection = ProviderSelectionResult(
            initialCandidates = listOf(Provider.TOSS, Provider.KAKAOPAY),
            filteredOutProviders = emptyList(),
            candidates = listOf(Provider.KAKAOPAY),
            selectedPrimaryProvider = Provider.TOSS,
            selectedPrimaryReason = SelectionPrimaryReason.USER_PREFERRED
        )

        val summary = SelectionSummary.approved(
            selection = selection,
            approvedProvider = Provider.KAKAOPAY,
            fallbackReasonCode = FallbackReasonCode.PROVIDER_TECHNICAL_FAILURE
        )

        assertEquals(FallbackReasonCode.PROVIDER_TECHNICAL_FAILURE, summary.fallbackReasonCode)
        assertEquals(Provider.KAKAOPAY, summary.finalApprovedProvider)
    }

    @Test
    fun `SelectionSummary sandbox factory는 fallbackReasonCode가 null이다`() {
        val summary = SelectionSummary.sandbox()
        assertEquals(null, summary.fallbackReasonCode)
    }
}
