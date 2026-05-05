package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.domain.model.Merchant
import com.pg.ochestration.infrastructure.persistence.jpa.entity.MerchantJpaEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class MerchantAdapter(
    private val jpaRepository: MerchantJpaRepository
) : MerchantRepository {

    @Transactional
    override fun save(merchant: Merchant): Merchant {
        val entity = jpaRepository.findById(merchant.merchantId).orElse(null)
            ?: return jpaRepository.save(MerchantJpaEntity.from(merchant)).toDomain()

        entity.applyDomainChanges(merchant)
        return jpaRepository.save(entity).toDomain()
    }

    override fun findById(merchantId: String): Merchant? =
        jpaRepository.findById(merchantId).orElse(null)?.toDomain()

    override fun findByIdForUpdate(merchantId: String): Merchant? =
        jpaRepository.findByIdForUpdate(merchantId).orElse(null)?.toDomain()

    override fun findByEmail(email: String): Merchant? =
        jpaRepository.findByEmail(email).orElse(null)?.toDomain()

    override fun existsByEmail(email: String): Boolean =
        jpaRepository.existsByEmail(email)
}
