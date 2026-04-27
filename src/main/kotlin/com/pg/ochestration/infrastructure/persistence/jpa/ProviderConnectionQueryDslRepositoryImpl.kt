package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.domain.model.ConnectionStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.infrastructure.persistence.jpa.entity.ProviderConnectionJpaEntity
import com.pg.ochestration.infrastructure.persistence.jpa.entity.QProviderConnectionJpaEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class ProviderConnectionQueryDslRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : ProviderConnectionQueryDslRepository {

    override fun findByProvider(provider: Provider): ProviderConnectionJpaEntity? {
        val connection = QProviderConnectionJpaEntity.providerConnectionJpaEntity
        return queryFactory
            .selectFrom(connection)
            .where(connection.provider.eq(provider))
            .fetchOne()
    }

    override fun getConnectionStatus(provider: Provider): ConnectionStatus {
        return findByProvider(provider)?.status ?: ConnectionStatus.DISCONNECTED
    }
}
