package com.pg.ochestration.infrastructure.persistence.jpa.entity

import com.pg.ochestration.domain.model.Provider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "payment_selection_initial_candidates")
class SelectionInitialCandidateJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selection_summary_id", nullable = false)
    val selectionSummary: SelectionSummaryJpaEntity,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    val provider: Provider,

    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectionInitialCandidateJpaEntity) return false
        return id != 0L && id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
