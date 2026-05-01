package com.pg.ochestration.infrastructure.persistence.jpa.entity

import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.ProviderHealthStatus
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
@Table(name = "provider_health")
@EntityListeners(AuditingEntityListener::class)
class ProviderHealthJpaEntity(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    val provider: Provider,

    initialHealthStatus: ProviderHealthStatus,

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
) {
    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 50)
    final var healthStatus: ProviderHealthStatus = initialHealthStatus
        private set

    fun updateHealth(status: ProviderHealthStatus) {
        this.healthStatus = status
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProviderHealthJpaEntity) return false
        return provider == other.provider
    }

    override fun hashCode(): Int = provider.hashCode()
}
