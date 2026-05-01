package com.pg.ochestration.domain.exception

import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.ApiKeyStatus

sealed class AuthException(
    message: String,
    val errorCode: String
) : RuntimeException(message)

class MissingApiKeyException
    : AuthException("X-Api-Key 헤더가 필요합니다", "MISSING_API_KEY")

class InvalidApiKeyException
    : AuthException("유효하지 않은 API Key입니다", "INVALID_API_KEY")

class ExpiredApiKeyException(keyId: String)
    : AuthException("만료된 API Key입니다: $keyId", "API_KEY_EXPIRED")

class RevokedApiKeyException(keyId: String)
    : AuthException("폐기된 API Key입니다: $keyId", "API_KEY_REVOKED")

class InvalidApiKeyStateException(keyId: String, currentStatus: ApiKeyStatus)
    : AuthException(
        "현재 상태($currentStatus)에서는 해당 작업을 수행할 수 없습니다: $keyId",
        "INVALID_API_KEY_STATE"
    )

class EnvironmentMismatchException(keyEnv: ApiKeyEnvironment, requestEnv: ApiKeyEnvironment)
    : AuthException(
        "'$keyEnv' 환경의 API Key는 '$requestEnv' 환경에서 사용할 수 없습니다",
        "ENVIRONMENT_MISMATCH"
    )

class PaymentAccessDeniedException(paymentId: String, merchantId: String)
    : AuthException(
        "가맹점 '$merchantId'는 결제 '$paymentId'에 접근 권한이 없습니다",
        "PAYMENT_ACCESS_DENIED"
    )
