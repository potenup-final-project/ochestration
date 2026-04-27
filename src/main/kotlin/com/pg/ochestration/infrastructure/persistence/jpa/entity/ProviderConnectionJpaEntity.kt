package com.pg.ochestration.infrastructure.persistence.jpa.entity

import com.pg.ochestration.domain.model.ConnectionStatus
import com.pg.ochestration.domain.model.Provider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "provider_connections")
@EntityListeners(AuditingEntityListener::class)
class ProviderConnectionJpaEntity(
    @Id
    @Column(name = "provider_connection_id", nullable = false)
    val providerConnectionId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", unique = true, nullable = false, length = 50)
    val provider: Provider,

    initialMerchantId: String,
    initialDisplayName: String,
    initialStatus: ConnectionStatus,

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
) {
    @Column(name = "merchant_id", nullable = false)
    final var merchantId: String = initialMerchantId
        private set

    @Column(name = "display_name", nullable = false)
    final var displayName: String = initialDisplayName
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    final var status: ConnectionStatus = initialStatus
        private set

    fun update(merchantId: String, displayName: String, status: ConnectionStatus) {
        this.merchantId = merchantId
        this.displayName = displayName
        this.status = status
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProviderConnectionJpaEntity) return false
        return providerConnectionId == other.providerConnectionId
    }

    override fun hashCode(): Int = providerConnectionId.hashCode()
}
