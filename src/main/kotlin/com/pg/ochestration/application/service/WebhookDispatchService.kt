package com.pg.ochestration.application.service

import com.pg.ochestration.application.port.out.WebhookDeliveryRepository
import com.pg.ochestration.application.port.out.WebhookEndpointRepository
import com.pg.ochestration.application.port.out.WebhookHttpClient
import com.pg.ochestration.application.port.out.WebhookSigner
import com.pg.ochestration.application.port.out.WebhookUrlValidator
import com.pg.ochestration.domain.model.WebhookDelivery
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant

@Service
@EnableConfigurationProperties(WebhookDispatchProperties::class)
@ConditionalOnProperty(prefix = "webhook.dispatch", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class WebhookDispatchService(
    private val webhookDeliveryRepository: WebhookDeliveryRepository,
    private val webhookEndpointRepository: WebhookEndpointRepository,
    private val webhookHttpClient: WebhookHttpClient,
    private val webhookSigner: WebhookSigner,
    private val webhookUrlValidator: WebhookUrlValidator,
    private val properties: WebhookDispatchProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${webhook.dispatch.fixed-delay-ms:10000}",
        initialDelayString = "\${webhook.dispatch.fixed-delay-ms:10000}"
    )
    fun dispatchDue() {
        dispatchDue(Instant.now())
    }

    fun dispatchDue(now: Instant) {
        val deliveries = webhookDeliveryRepository.findDueForDispatch(now, properties.batchSize)
        deliveries.forEach { delivery ->
            dispatchOne(delivery, now)
        }
    }

    private fun dispatchOne(delivery: WebhookDelivery, now: Instant) {
        val updated = runCatching {
            val endpoint = requireNotNull(webhookEndpointRepository.findById(delivery.endpointId)) {
                "웹훅 엔드포인트를 찾을 수 없습니다: endpointId=${delivery.endpointId}"
            }
            val urlValidation = webhookUrlValidator.validate(endpoint.url)
            if (!urlValidation.allowed) {
                return@runCatching delivery.markDead(
                    error = "웹훅 URL 보안 검증 실패: ${urlValidation.reason ?: "허용되지 않는 URL"}",
                    responseCode = null,
                    now = now
                )
            }
            val signature = webhookSigner.sign(
                secret = endpoint.signingSecret,
                eventId = delivery.eventId,
                payload = delivery.payload
            )
            val response = webhookHttpClient.post(endpoint, delivery, signature)

            if (response.isSuccessful()) {
                delivery.markSent(response.statusCode, now)
            } else {
                delivery.markFailed("웹훅 HTTP 응답 실패: status=${response.statusCode}", response.statusCode, now)
            }
        }.getOrElse { ex ->
            logger.warn(
                "웹훅 발송 실패: deliveryId={}, endpointId={}, attempt={}, error={}",
                delivery.deliveryId,
                delivery.endpointId,
                delivery.attemptCount + 1,
                ex.message
            )
            delivery.markFailed(ex.message ?: "웹훅 발송 실패", null, now)
        }

        webhookDeliveryRepository.save(updated)
    }
}
