package com.pg.ochestration.domain.model

data class SelectionSummary(
    val initialCandidates: List<Provider>,
    val filteredOutProviders: List<FilteredOutProvider>,
    val selectedPrimaryProvider: Provider?,
    val selectedPrimaryReason: String,
    val fallbackReason: String?,
    val finalApprovedProvider: Provider?
)
