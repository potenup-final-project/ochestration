package com.pg.ochestration.infrastructure.idempotency

import com.pg.ochestration.application.port.out.MerchantApiKeyRepository
import com.pg.ochestration.infrastructure.auth.ApiKeyCache
import com.pg.ochestration.infrastructure.auth.ApiKeyHasher
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import tools.jackson.databind.ObjectMapper

private const val HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"
private const val HEADER_API_KEY = "X-Api-Key"
private val APPROVE_PATH_REGEX = Regex("^/api/payments/approve$")
private val CANCEL_PATH_REGEX = Regex("^/api/payments/[^/]+/cancel$")

/**
 * POST 결제 요청에 대해 Redis 기반 멱등성을 처리하는 서블릿 필터.
 *
 * 필터는 DispatcherServlet 앞에 위치하므로 HandlerInterceptor(ApiKeyAuthInterceptor)보다
 * 먼저 실행된다. MerchantContext가 아직 없기 때문에 X-Api-Key 헤더를 직접 해시하여
 * Redis 키의 가맹점 식별자로 사용한다.
 *
 * 처리 흐름:
 * 1. Idempotency-Key 헤더 없음 → 필터 통과 (멱등성 체크 생략)
 * 2. Redis에 키 없음 → PROCESSING 선점 → 요청 통과 → 응답 캡처 후 COMPLETED 저장
 * 3. Redis 상태 PROCESSING → 409 즉시 반환
 * 4. Redis 상태 COMPLETED → 캐시된 응답 그대로 반환
 */
class IdempotencyFilter(
    private val idempotencyRedisStore: IdempotencyRedisStore,
    private val apiKeyHasher: ApiKeyHasher,
    private val merchantApiKeyRepository: MerchantApiKeyRepository,
    private val apiKeyCache: ApiKeyCache,
    private val objectMapper: ObjectMapper,
    @Value("\${auth.api-key.enabled:true}") private val authEnabled: Boolean
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val operation = resolveOperation(request)
        if (operation == null || !authEnabled) {
            filterChain.doFilter(request, response)
            return
        }

        val idempotencyKey = request.getHeader(HEADER_IDEMPOTENCY_KEY)
        if (idempotencyKey.isNullOrBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        val rawApiKey = request.getHeader(HEADER_API_KEY)
        if (rawApiKey.isNullOrBlank()) {
            // API Key 없는 요청은 ApiKeyAuthInterceptor에서 거부되므로 통과시킨다
            filterChain.doFilter(request, response)
            return
        }
        val principal = resolvePrincipal(rawApiKey)
        if (principal == null) {
            filterChain.doFilter(request, response)
            return
        }

        val context = IdempotencyContext(
            merchantId = principal.merchantId,
            idempotencyKey = idempotencyKey,
            operation = operation
        )
        val redisKey = idempotencyRedisStore.buildRedisKey(context)

        val existing = idempotencyRedisStore.find(redisKey)

        when (existing?.status) {
            IdempotencyStatus.COMPLETED -> {
                log.debug(
                    "멱등성 캐시 히트(COMPLETED): idempotencyKey={}, httpStatus={}",
                    idempotencyKey, existing.httpStatus
                )
                writeCachedResponse(response, existing)
                return
            }
            IdempotencyStatus.PROCESSING -> {
                log.warn(
                    "멱등성 충돌(PROCESSING): idempotencyKey={}",
                    idempotencyKey
                )
                writeProcessingConflict(response)
                return
            }
            null -> {
                val acquired = idempotencyRedisStore.tryAcquire(context)
                if (!acquired) {
                    // tryAcquire와 find 사이의 아주 좁은 경합 구간 — PROCESSING으로 처리
                    log.warn(
                        "멱등성 선점 실패(경합): idempotencyKey={}",
                        idempotencyKey
                    )
                    writeProcessingConflict(response)
                    return
                }
            }
        }

        val cachingResponse = ContentCachingResponseWrapper(response)
        try {
            filterChain.doFilter(request, cachingResponse)
        } finally {
            val httpStatus = cachingResponse.status
            val body = String(cachingResponse.contentAsByteArray, Charsets.UTF_8)

            runCatching {
                idempotencyRedisStore.complete(context, httpStatus, body)
            }.onFailure { ex ->
                log.error(
                    "멱등성 완료 상태 저장 실패 (Redis 오류): idempotencyKey={}, error={}",
                    idempotencyKey, ex.message
                )
                // Redis 저장 실패는 응답에 영향을 주지 않는다 — 클라이언트는 정상 응답을 받는다
            }

            cachingResponse.copyBodyToResponse()
        }
    }

    private fun writeCachedResponse(response: HttpServletResponse, record: IdempotencyRecord) {
        response.status = record.httpStatus ?: HttpStatus.OK.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(record.body ?: "")
    }

    private fun writeProcessingConflict(response: HttpServletResponse) {
        response.status = HttpStatus.CONFLICT.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(
            objectMapper.writeValueAsString(
                mapOf(
                    "errorCode" to "IDEMPOTENCY_PROCESSING",
                    "message" to "동일한 멱등성 키로 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."
                )
            )
        )
    }

    private fun resolveOperation(request: HttpServletRequest): IdempotencyOperation? {
        if (request.method != "POST") return null
        return when {
            APPROVE_PATH_REGEX.matches(request.requestURI) -> IdempotencyOperation.APPROVE
            CANCEL_PATH_REGEX.matches(request.requestURI) -> IdempotencyOperation.CANCEL
            else -> null
        }
    }

    private fun resolvePrincipal(rawApiKey: String): MerchantPrincipal? {
        val keyHash = apiKeyHasher.hash(rawApiKey)
        apiKeyCache.get(keyHash)?.let { return it }

        val apiKey = merchantApiKeyRepository.findActiveByHash(keyHash) ?: return null
        return runCatching {
            apiKey.ensureNotExpired()
            if (!apiKey.isActive()) return null
            MerchantPrincipal(
                merchantId = apiKey.merchantId,
                environment = apiKey.environment
            ).also { apiKeyCache.put(keyHash, it) }
        }.getOrNull()
    }
}
