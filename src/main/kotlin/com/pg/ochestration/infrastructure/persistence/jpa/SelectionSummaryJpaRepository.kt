package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.infrastructure.persistence.jpa.entity.SelectionSummaryJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SelectionSummaryJpaRepository : JpaRepository<SelectionSummaryJpaEntity, Long> {
    fun findByPaymentPaymentId(paymentId: String): SelectionSummaryJpaEntity?
}
