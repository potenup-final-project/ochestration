package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.infrastructure.persistence.jpa.entity.PaymentJpaEntity
import com.pg.ochestration.infrastructure.persistence.jpa.entity.QPaymentJpaEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class PaymentQueryDslRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : PaymentQueryDslRepository {

    override fun findByPaymentId(paymentId: String): PaymentJpaEntity? {
        val payment = QPaymentJpaEntity.paymentJpaEntity
        return queryFactory
            .selectFrom(payment)
            .where(payment.paymentId.eq(paymentId))
            .fetchOne()
    }
}
