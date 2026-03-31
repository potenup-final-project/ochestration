package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.infrastructure.persistence.jpa.entity.PaymentJpaEntity

interface PaymentQueryDslRepository {
    fun findByPaymentId(paymentId: String): PaymentJpaEntity?
}
