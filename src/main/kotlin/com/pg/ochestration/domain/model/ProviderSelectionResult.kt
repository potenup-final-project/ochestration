package com.pg.ochestration.domain.model

data class ProviderSelectionResult(
    val initialCandidates: List<Provider>,
    val filteredOutProviders: List<FilteredOutProvider>,
    val candidates: List<Provider>,
    val selectedPrimaryProvider: Provider?,
    val selectedPrimaryReason: SelectionPrimaryReason
)
