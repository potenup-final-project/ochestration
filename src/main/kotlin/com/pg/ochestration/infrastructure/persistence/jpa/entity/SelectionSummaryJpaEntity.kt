package com.pg.ochestration.infrastructure.persistence.jpa.entity

import com.pg.ochestration.domain.model.Provider
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "payment_selection_summaries")
class SelectionSummaryJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    val payment: PaymentJpaEntity,

    initialSelectedPrimaryProvider: Provider?,
    initialSelectedPrimaryReason: String,
    initialFallbackReason: String?,
    initialFinalApprovedProvider: Provider?
) {
    @Enumerated(EnumType.STRING)
    @Column(name = "selected_primary_provider", nullable = true, length = 50)
    final var selectedPrimaryProvider: Provider? = initialSelectedPrimaryProvider
        private set

    @Column(name = "selected_primary_reason", nullable = false, length = 500)
    final var selectedPrimaryReason: String = initialSelectedPrimaryReason
        private set

    @Column(name = "fallback_reason", nullable = true, length = 500)
    final var fallbackReason: String? = initialFallbackReason
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "final_approved_provider", nullable = true, length = 50)
    final var finalApprovedProvider: Provider? = initialFinalApprovedProvider
        private set

    @OneToMany(
        mappedBy = "selectionSummary",
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        fetch = FetchType.LAZY,
        orphanRemoval = true
    )
    private val _initialCandidates: MutableList<SelectionInitialCandidateJpaEntity> = mutableListOf()

    val initialCandidates: List<SelectionInitialCandidateJpaEntity> get() = _initialCandidates

    @OneToMany(
        mappedBy = "selectionSummary",
        cascade = [CascadeType.PERSIST, CascadeType.MERGE],
        fetch = FetchType.LAZY,
        orphanRemoval = true
    )
    private val _filteredOutProviders: MutableList<SelectionFilteredOutProviderJpaEntity> = mutableListOf()

    val filteredOutProviders: List<SelectionFilteredOutProviderJpaEntity> get() = _filteredOutProviders

    fun syncSelectionData(
        selectedPrimaryProvider: Provider?,
        selectedPrimaryReason: String,
        fallbackReason: String?,
        finalApprovedProvider: Provider?,
        initialCandidates: List<SelectionInitialCandidateJpaEntity>,
        filteredOutProviders: List<SelectionFilteredOutProviderJpaEntity>
    ) {
        this.selectedPrimaryProvider = selectedPrimaryProvider
        this.selectedPrimaryReason = selectedPrimaryReason
        this.fallbackReason = fallbackReason
        this.finalApprovedProvider = finalApprovedProvider
        _initialCandidates.clear()
        _initialCandidates.addAll(initialCandidates)
        _filteredOutProviders.clear()
        _filteredOutProviders.addAll(filteredOutProviders)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectionSummaryJpaEntity) return false
        return id != 0L && id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
