package com.pg.ochestration.infrastructure.persistence.jpa.entity

import com.pg.ochestration.domain.model.Merchant
import com.pg.ochestration.domain.model.MerchantStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@Table(
    name = "merchants",
    indexes = [
        Index(name = "uk_merchants_email", columnList = "email", unique = true),
        Index(name = "idx_merchants_status", columnList = "status")
    ]
)
@EntityListeners(AuditingEntityListener::class)
class MerchantJpaEntity(
    @Id
    @Column(name = "merchant_id", length = 36, nullable = false)
    val merchantId: String,

    @Column(name = "email", length = 255, nullable = false, unique = true)
    val email: String,

    @Column(name = "password_hash", length = 255, nullable = false)
    val passwordHash: String,

    initialBusinessName: String?,
    initialBusinessRegistrationNumber: String?,
    initialBusinessRegistrationFileUrl: String?,
    initialStatus: MerchantStatus,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
) {
    @Column(name = "business_name", length = 255, nullable = true)
    final var businessName: String? = initialBusinessName
        private set

    @Column(name = "business_registration_number", length = 20, nullable = true)
    final var businessRegistrationNumber: String? = initialBusinessRegistrationNumber
        private set

    @Column(name = "business_registration_file_url", length = 500, nullable = true)
    final var businessRegistrationFileUrl: String? = initialBusinessRegistrationFileUrl
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    final var status: MerchantStatus = initialStatus
        private set

    fun applyDomainChanges(merchant: Merchant) {
        this.status = merchant.status
        this.businessName = merchant.businessName
        this.businessRegistrationNumber = merchant.businessRegistrationNumber
        this.businessRegistrationFileUrl = merchant.businessRegistrationFileUrl
    }

    fun toDomain(): Merchant = Merchant(
        merchantId = merchantId,
        email = email,
        passwordHash = passwordHash,
        businessName = businessName,
        businessRegistrationNumber = businessRegistrationNumber,
        businessRegistrationFileUrl = businessRegistrationFileUrl,
        status = status,
        createdAt = createdAt ?: Instant.now(),
        updatedAt = updatedAt ?: Instant.now()
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MerchantJpaEntity) return false
        return merchantId == other.merchantId
    }

    override fun hashCode(): Int = merchantId.hashCode()

    companion object {
        fun from(merchant: Merchant): MerchantJpaEntity = MerchantJpaEntity(
            merchantId = merchant.merchantId,
            email = merchant.email,
            passwordHash = merchant.passwordHash,
            initialBusinessName = merchant.businessName,
            initialBusinessRegistrationNumber = merchant.businessRegistrationNumber,
            initialBusinessRegistrationFileUrl = merchant.businessRegistrationFileUrl,
            initialStatus = merchant.status
        )
    }
}
