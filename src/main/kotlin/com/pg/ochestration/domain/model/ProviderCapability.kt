package com.pg.ochestration.domain.model

data class ProviderCapability(
    val approve: Boolean,
    val cancel: Boolean,
    val getPayment: Boolean,
    val billing: Boolean,
    val settlement: Boolean
)
