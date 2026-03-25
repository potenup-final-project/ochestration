package com.pg.ochestration.infrastructure.gateway.pg

import com.pg.ochestration.application.port.out.GatewayApproveCommand
import com.pg.ochestration.application.port.out.GatewayApproveResult
import com.pg.ochestration.application.port.out.GatewayCancelCommand
import com.pg.ochestration.application.port.out.GatewayCancelResult
import com.pg.ochestration.application.port.out.GatewayFailure
import com.pg.ochestration.application.port.out.GatewayPaymentQuery
import com.pg.ochestration.application.port.out.GatewayPaymentResult
import com.pg.ochestration.application.port.out.PaymentProviderGateway
import com.pg.ochestration.domain.service.FailureClassifier
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import io.netty.channel.ChannelOption
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.netty.http.client.HttpClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant

@Component
@EnableConfigurationProperties(TossGatewayProperties::class)
class TossPgAdapter(
    private val failureClassifier: FailureClassifier,
    private val tossProperties: TossGatewayProperties,
    private val objectMapper: ObjectMapper
) : PaymentProviderGateway {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val webClient: WebClient = WebClient.builder()
        .baseUrl(tossProperties.baseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .clientConnector(
            ReactorClientHttpConnector(
                HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tossProperties.connectTimeoutMs.toInt())
                    .responseTimeout(Duration.ofMillis(tossProperties.readTimeoutMs))
            )
        )
        .defaultHeaders { headers ->
            if (tossProperties.secretKey.isNotBlank()) {
                headers.setBasicAuth(tossProperties.secretKey, "")
            }
        }
        .build()

    override fun supports(provider: Provider): Boolean = provider == Provider.TOSS

    override suspend fun approve(command: GatewayApproveCommand): GatewayApproveResult {
        val paymentKey = command.metadata["paymentKey"]
            ?: return missingFieldFailure("paymentKey", Provider.TOSS)

        val body = mapOf(
            "paymentKey" to paymentKey,
            "orderId" to command.orderId,
            "amount" to command.amount
        )

        return runCatching {
            val responseBody = webClient.post()
                .uri("/v1/payments/confirm")
                .header("Idempotency-Key", command.idempotencyKey)
                .bodyValue(body)
                .retrieve()
                .awaitBody<String>()

            val json = objectMapper.readTree(responseBody)
            GatewayApproveResult(
                success = true,
                provider = Provider.TOSS,
                providerTxId = json.path("paymentKey").asText(paymentKey),
                approvedAt = parseInstant(json.path("approvedAt").asText(null)),
                status = PaymentStatus.APPROVED,
                metadata = mapOf(
                    "tossOrderName" to json.path("orderName").asText(""),
                    "tossMethod" to json.path("method").asText("")
                )
            )
        }.getOrElse { throwable ->
            toApproveFailure(throwable)
        }
    }

    override suspend fun cancel(command: GatewayCancelCommand): GatewayCancelResult {
        val body = mapOf("cancelReason" to command.reason)
        return runCatching {
            val responseBody = webClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", command.providerTxId)
                .header("Idempotency-Key", command.idempotencyKey)
                .bodyValue(body)
                .retrieve()
                .awaitBody<String>()

            val json = objectMapper.readTree(responseBody)
            GatewayCancelResult(
                success = true,
                provider = Provider.TOSS,
                providerTxId = command.providerTxId,
                canceledAt = parseInstant(json.path("cancels").firstOrNull()?.path("canceledAt")?.asText(null)),
                status = PaymentStatus.CANCELED,
                metadata = mapOf("cancelStatus" to json.path("status").asText("DONE"))
            )
        }.getOrElse { throwable ->
            toCancelFailure(command.providerTxId, throwable)
        }
    }

    override suspend fun getPayment(query: GatewayPaymentQuery): GatewayPaymentResult {
        return runCatching {
            val responseBody = webClient.get()
                .uri("/v1/payments/{paymentKey}", query.providerTxId)
                .retrieve()
                .awaitBody<String>()

            val json = objectMapper.readTree(responseBody)
            val status = when (json.path("status").asText()) {
                "DONE" -> PaymentStatus.APPROVED
                "CANCELED" -> PaymentStatus.CANCELED
                else -> PaymentStatus.READY
            }

            GatewayPaymentResult(
                success = true,
                provider = Provider.TOSS,
                providerTxId = query.providerTxId,
                status = status,
                approvedAt = parseInstant(json.path("approvedAt").asText(null)),
                canceledAt = parseInstant(json.path("cancels").firstOrNull()?.path("canceledAt")?.asText(null)),
                metadata = mapOf("tossStatus" to json.path("status").asText(""))
            )
        }.getOrElse { throwable ->
            val failure = toFailure(throwable)
            GatewayPaymentResult(
                success = false,
                provider = Provider.TOSS,
                providerTxId = query.providerTxId,
                status = PaymentStatus.FAILED,
                failure = failure
            )
        }
    }

    private fun toApproveFailure(throwable: Throwable): GatewayApproveResult {
        val failure = toFailure(throwable)
        logger.warn("[TossGateway] approve failed code={} message={}", failure.code, failure.message)
        return GatewayApproveResult(
            success = false,
            provider = Provider.TOSS,
            status = PaymentStatus.FAILED,
            failure = failure
        )
    }

    private fun toCancelFailure(providerTxId: String, throwable: Throwable): GatewayCancelResult {
        val failure = toFailure(throwable)
        logger.warn("[TossGateway] cancel failed txId={} code={} message={}", providerTxId, failure.code, failure.message)
        return GatewayCancelResult(
            success = false,
            provider = Provider.TOSS,
            providerTxId = providerTxId,
            status = PaymentStatus.FAILED,
            failure = failure
        )
    }

    private fun toFailure(throwable: Throwable): GatewayFailure {
        val defaultCode = "TOSS_UNKNOWN_ERROR"
        if (throwable is WebClientResponseException) {
            val body = throwable.responseBodyAsString
            val json = runCatching { objectMapper.readTree(body) as JsonNode }.getOrNull()
            val code = json?.path("code")?.asText(null) ?: "HTTP_${throwable.statusCode.value()}"
            val message = json?.path("message")?.asText(null) ?: (throwable.message ?: "Unknown Toss error")
            return GatewayFailure(
                code = code,
                category = failureClassifier.classify(code),
                message = message
            )
        }

        return GatewayFailure(
            code = defaultCode,
            category = failureClassifier.classify(defaultCode),
            message = throwable.message ?: "Unknown Toss error"
        )
    }

    private fun missingFieldFailure(fieldName: String, provider: Provider): GatewayApproveResult {
        val code = "INVALID_REQUEST"
        return GatewayApproveResult(
            success = false,
            provider = provider,
            status = PaymentStatus.FAILED,
            failure = GatewayFailure(
                code = code,
                category = failureClassifier.classify(code),
                message = "$fieldName is required in metadata"
            )
        )
    }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
    }
}
