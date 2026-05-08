package com.pg.ochestration.application.service

import com.pg.ochestration.application.port.out.MerchantRepository
import com.pg.ochestration.application.port.out.WebhookEndpointRepository
import com.pg.ochestration.application.port.out.WebhookUrlValidator
import com.pg.ochestration.application.service.command.WebhookEndpointUpdateCommand
import com.pg.ochestration.application.service.result.WebhookEndpointCreateResult
import com.pg.ochestration.domain.exception.MerchantNotFoundException
import com.pg.ochestration.domain.exception.WebhookEndpointDescriptionTooLongException
import com.pg.ochestration.domain.exception.WebhookEndpointLimitExceededException
import com.pg.ochestration.domain.exception.WebhookEndpointNotFoundException
import com.pg.ochestration.domain.exception.WebhookEndpointUrlNotAllowedException
import com.pg.ochestration.domain.model.WebhookEndpoint
import com.pg.ochestration.domain.model.WebhookEndpointStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class WebhookEndpointService(
    private val merchantRepository: MerchantRepository,
    private val webhookEndpointRepository: WebhookEndpointRepository,
    private val webhookUrlValidator: WebhookUrlValidator,
    private val transactionTemplate: TransactionTemplate
) {

    fun create(merchantId: String, url: String, description: String?): WebhookEndpointCreateResult {
        validateUrl(url)
        validateDescription(description)
        return transactionTemplate.execute {
            merchantRepository.findByIdForUpdate(merchantId) ?: throw MerchantNotFoundException(merchantId)

            val count = webhookEndpointRepository.countByMerchantId(merchantId)
            if (count >= ENDPOINT_LIMIT) {
                throw WebhookEndpointLimitExceededException(merchantId, ENDPOINT_LIMIT)
            }

            val now = Instant.now()
            val endpoint = WebhookEndpoint(
                endpointId = UUID.randomUUID().toString(),
                merchantId = merchantId,
                url = url,
                signingSecret = generateSigningSecret(),
                status = WebhookEndpointStatus.ACTIVE,
                description = description,
                createdAt = now,
                updatedAt = now
            )
            val saved = webhookEndpointRepository.save(endpoint)
            WebhookEndpointCreateResult(endpoint = saved, signingSecret = saved.signingSecret)
        } ?: error("웹훅 엔드포인트 생성 트랜잭션 결과가 없습니다")
    }

    @Transactional(readOnly = true)
    fun get(merchantId: String, endpointId: String): WebhookEndpoint =
        findOwned(merchantId, endpointId)

    @Transactional(readOnly = true)
    fun list(merchantId: String): List<WebhookEndpoint> =
        webhookEndpointRepository.findAllByMerchantId(merchantId)

    fun update(
        command: WebhookEndpointUpdateCommand
    ): WebhookEndpoint {
        if (command.url != null) validateUrl(command.url)
        validateDescription(command.description)
        return transactionTemplate.execute {
            val endpoint = findOwned(command.merchantId, command.endpointId)
            val updated = endpoint.update(
                url = command.url,
                status = command.status,
                description = command.description,
                now = Instant.now()
            )
            webhookEndpointRepository.save(updated)
        } ?: error("웹훅 엔드포인트 수정 트랜잭션 결과가 없습니다")
    }

    @Transactional
    fun deactivate(merchantId: String, endpointId: String): WebhookEndpoint {
        findOwned(merchantId, endpointId)
        return webhookEndpointRepository.deactivate(endpointId, Instant.now())
            ?: throw WebhookEndpointNotFoundException(endpointId)
    }

    private fun findOwned(merchantId: String, endpointId: String): WebhookEndpoint {
        val endpoint = webhookEndpointRepository.findById(endpointId)
            ?: throw WebhookEndpointNotFoundException(endpointId)
        if (endpoint.merchantId != merchantId) {
            throw WebhookEndpointNotFoundException(endpointId)
        }
        return endpoint
    }

    private fun validateUrl(url: String) {
        val validation = webhookUrlValidator.validate(url)
        if (!validation.allowed) {
            throw WebhookEndpointUrlNotAllowedException(validation.reason ?: "허용되지 않는 URL")
        }
    }

    private fun validateDescription(description: String?) {
        if (description != null && description.length > DESCRIPTION_LIMIT) {
            throw WebhookEndpointDescriptionTooLongException(description.length, DESCRIPTION_LIMIT)
        }
    }

    private fun generateSigningSecret(): String {
        val bytes = ByteArray(SIGNING_SECRET_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val ENDPOINT_LIMIT = 5
        const val DESCRIPTION_LIMIT = 255
        const val SIGNING_SECRET_BYTES = 32
        val secureRandom = SecureRandom()
    }
}
