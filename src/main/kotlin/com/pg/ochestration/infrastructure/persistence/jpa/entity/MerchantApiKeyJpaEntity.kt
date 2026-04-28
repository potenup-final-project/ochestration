package com.pg.ochestration.infrastructure.persistence.jpa.entity

import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.ApiKeyScope
import com.pg.ochestration.domain.model.ApiKeyStatus
import com.pg.ochestration.domain.model.MerchantApiKey
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "merchant_api_keys",
    indexes = [
        Index(name = "uk_key_hash", columnList = "key_hash", unique = true),
        Index(name = "idx_merchant_status", columnList = "merchant_id, status"),
        Index(name = "idx_merchant_env_status", columnList = "merchant_id, environment, status")
    ]
)
class MerchantApiKeyJpaEntity(
    @Id
    @Column(name = "key_id", length = 36, nullable = false)
    val keyId: String,

    @Column(name = "merchant_id", length = 100, nullable = false)
    val merchantId: String,

    @Column(name = "key_hash", length = 64, nullable = false, unique = true)
    val keyHash: String,

    @Column(name = "key_prefix", length = 20, nullable = false)
    val keyPrefix: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", length = 10, nullable = false)
    val environment: ApiKeyEnvironment,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    val status: ApiKeyStatus,

    @Convert(converter = ApiKeyScopeSetConverter::class)
    @Column(name = "scopes", length = 500, nullable = false)
    val scopes: Set<ApiKeyScope>,

    @Column(name = "description", length = 255)
    val description: String?,

    @Column(name = "expired_at")
    val expiredAt: Instant?,

    @Column(name = "grace_expired_at")
    val graceExpiredAt: Instant?,

    @Column(name = "revoked_at")
    val revokedAt: Instant?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "last_used_at")
    val lastUsedAt: Instant?
) {
    fun toDomain(): MerchantApiKey = MerchantApiKey(
        keyId = keyId,
        merchantId = merchantId,
        keyHash = keyHash,
        keyPrefix = keyPrefix,
        environment = environment,
        status = status,
        scopes = scopes,
        description = description,
        expiredAt = expiredAt,
        graceExpiredAt = graceExpiredAt,
        revokedAt = revokedAt,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt
    )

    companion object {
        fun from(domain: MerchantApiKey): MerchantApiKeyJpaEntity = MerchantApiKeyJpaEntity(
            keyId = domain.keyId,
            merchantId = domain.merchantId,
            keyHash = domain.keyHash,
            keyPrefix = domain.keyPrefix,
            environment = domain.environment,
            status = domain.status,
            scopes = domain.scopes,
            description = domain.description,
            expiredAt = domain.expiredAt,
            graceExpiredAt = domain.graceExpiredAt,
            revokedAt = domain.revokedAt,
            createdAt = domain.createdAt,
            lastUsedAt = domain.lastUsedAt
        )
    }
}

@Converter
class ApiKeyScopeSetConverter : AttributeConverter<Set<ApiKeyScope>, String> {
    override fun convertToDatabaseColumn(attribute: Set<ApiKeyScope>?): String =
        attribute?.joinToString(",") { it.name } ?: ""

    override fun convertToEntityAttribute(dbData: String?): Set<ApiKeyScope> =
        if (dbData.isNullOrBlank()) emptySet()
        else dbData.split(",").mapNotNull {
            runCatching { ApiKeyScope.valueOf(it.trim()) }.getOrNull()
        }.toSet()
}
