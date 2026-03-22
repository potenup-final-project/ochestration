package com.pg.ochestration.domain.model

data class FilteredOutProvider(
    val provider: Provider,
    val reason: ProviderFilteredOutReason
)
