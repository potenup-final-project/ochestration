package com.pg.ochestration.infrastructure.auth

import com.pg.ochestration.application.port.out.MerchantApiKeyRepository
import com.pg.ochestration.domain.exception.ExpiredApiKeyException
import com.pg.ochestration.domain.exception.InvalidApiKeyException
import com.pg.ochestration.domain.exception.MissingApiKeyException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class ApiKeyAuthInterceptor(
    private val merchantApiKeyRepository: MerchantApiKeyRepository,
    private val apiKeyHasher: ApiKeyHasher,
    private val apiKeyCache: ApiKeyCache,
    @Value("\${auth.api-key.enabled:true}") private val enabled: Boolean
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        if (!enabled) {
            log.warn("API Key 인증이 비활성화되어 있습니다. 프로덕션 환경에서는 반드시 활성화하세요.")
            return true
        }

        val rawKey = request.getHeader("X-Api-Key")
            ?: throw MissingApiKeyException()

        val keyHash = apiKeyHasher.hash(rawKey)

        val cached = apiKeyCache.get(keyHash)
        if (cached != null) {
            request.setAttribute(MerchantContext.ATTR_KEY, cached)
            return true
        }

        val apiKey = merchantApiKeyRepository.findActiveByHash(keyHash)
            ?: throw InvalidApiKeyException()

        apiKey.ensureNotExpired()

        if (!apiKey.isActive()) throw ExpiredApiKeyException(apiKey.keyId)

        val principal = MerchantPrincipal(
            merchantId = apiKey.merchantId,
            environment = apiKey.environment
        )
        apiKeyCache.put(keyHash, principal)
        request.setAttribute(MerchantContext.ATTR_KEY, principal)

        log.debug(
            "API Key 인증 성공: merchantId={}, 환경={}, keyPrefix={}",
            apiKey.merchantId, apiKey.environment, apiKey.keyPrefix
        )

        return true
    }
}
