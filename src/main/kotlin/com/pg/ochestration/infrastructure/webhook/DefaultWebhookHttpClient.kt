package com.pg.ochestration.infrastructure.webhook

import com.pg.ochestration.application.port.out.WebhookHttpClient
import com.pg.ochestration.application.port.out.WebhookHttpResponse
import com.pg.ochestration.application.service.WebhookDispatchProperties
import com.pg.ochestration.domain.model.WebhookDelivery
import com.pg.ochestration.domain.model.WebhookEndpoint
import io.netty.channel.ChannelOption
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Component
@EnableConfigurationProperties(WebhookDispatchProperties::class)
class DefaultWebhookHttpClient(
    properties: WebhookDispatchProperties
) : WebhookHttpClient {
    private val responseTimeout = Duration.ofMillis(properties.readTimeoutMs)
    private val webClient: WebClient = WebClient.builder()
        .clientConnector(
            ReactorClientHttpConnector(
                HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeoutMs.toInt())
                    .responseTimeout(responseTimeout)
            )
        )
        .build()

    override fun post(endpoint: WebhookEndpoint, delivery: WebhookDelivery, signature: String): WebhookHttpResponse {
        val statusCode = webClient.post()
            .uri(endpoint.url)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(WEBHOOK_SIGNATURE_HEADER, "sha256=$signature")
            .header(WEBHOOK_EVENT_HEADER, delivery.eventType.value)
            .header(WEBHOOK_EVENT_ID_HEADER, delivery.eventId)
            .header(WEBHOOK_DELIVERY_ID_HEADER, delivery.deliveryId)
            .bodyValue(delivery.payload)
            .exchangeToMono { response ->
                response.releaseBody().thenReturn(response.statusCode().value())
            }
            .block(responseTimeout.plusMillis(500))
            ?: error("웹훅 HTTP 응답을 받지 못했습니다: deliveryId=${delivery.deliveryId}")

        return WebhookHttpResponse(statusCode)
    }

    companion object {
        const val WEBHOOK_SIGNATURE_HEADER = "X-Webhook-Signature"
        const val WEBHOOK_EVENT_HEADER = "X-Webhook-Event"
        const val WEBHOOK_EVENT_ID_HEADER = "X-Webhook-Event-Id"
        const val WEBHOOK_DELIVERY_ID_HEADER = "X-Webhook-Delivery-Id"
    }
}
