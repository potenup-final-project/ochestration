package com.pg.ochestration.domain.model

data class SelectionSummary(
    val initialCandidates: List<Provider>,
    val filteredOutProviders: List<FilteredOutProvider>,
    val selectedPrimaryProvider: Provider?,
    val selectedPrimaryReason: SelectionPrimaryReason,
    val fallbackReasonCode: FallbackReasonCode?,
    val finalApprovedProvider: Provider?
) {
    companion object {
        /** PgOrchestrator: 후보 없음으로 즉시 실패 */
        fun noProvider(
            initialCandidates: List<Provider>,
            filteredOutProviders: List<FilteredOutProvider>
        ): SelectionSummary = SelectionSummary(
            initialCandidates = initialCandidates,
            filteredOutProviders = filteredOutProviders,
            selectedPrimaryProvider = null,
            selectedPrimaryReason = SelectionPrimaryReason.NO_ELIGIBLE_PROVIDER,
            fallbackReasonCode = FallbackReasonCode.NO_PROVIDER_AVAILABLE,
            finalApprovedProvider = null
        )

        /** PgOrchestrator: 특정 Provider로 승인 성공 */
        fun approved(
            selection: ProviderSelectionResult,
            approvedProvider: Provider,
            fallbackReasonCode: FallbackReasonCode?
        ): SelectionSummary = SelectionSummary(
            initialCandidates = selection.initialCandidates,
            filteredOutProviders = selection.filteredOutProviders,
            selectedPrimaryProvider = selection.selectedPrimaryProvider,
            selectedPrimaryReason = selection.selectedPrimaryReason,
            fallbackReasonCode = fallbackReasonCode,
            finalApprovedProvider = approvedProvider
        )

        /** PgOrchestrator: 모든 후보 소진 후 최종 실패 */
        fun exhausted(
            selection: ProviderSelectionResult,
            fallbackReasonCode: FallbackReasonCode
        ): SelectionSummary = SelectionSummary(
            initialCandidates = selection.initialCandidates,
            filteredOutProviders = selection.filteredOutProviders,
            selectedPrimaryProvider = selection.selectedPrimaryProvider,
            selectedPrimaryReason = selection.selectedPrimaryReason,
            fallbackReasonCode = fallbackReasonCode,
            finalApprovedProvider = null
        )

        /** SandboxPaymentSimulator 전용 */
        fun sandbox(): SelectionSummary = SelectionSummary(
            initialCandidates = emptyList(),
            filteredOutProviders = emptyList(),
            selectedPrimaryProvider = Provider.TOSS,
            selectedPrimaryReason = SelectionPrimaryReason.HIGHEST_PRIORITY_DEFAULT,
            fallbackReasonCode = null,
            finalApprovedProvider = Provider.TOSS
        )
    }
}
