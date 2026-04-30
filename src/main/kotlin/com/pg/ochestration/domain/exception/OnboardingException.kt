package com.pg.ochestration.domain.exception

import com.pg.ochestration.domain.model.MerchantStatus

sealed class OnboardingException(
    message: String,
    val errorCode: String
) : RuntimeException(message)

class DuplicateEmailException(email: String)
    : OnboardingException("이미 사용 중인 이메일입니다: $email", "DUPLICATE_EMAIL")

class InvalidEmailTokenException(reason: String)
    : OnboardingException(reason, "INVALID_EMAIL_TOKEN")

class InvalidOnboardingTokenException(reason: String)
    : OnboardingException(reason, "INVALID_ONBOARDING_TOKEN")

class OnboardingTokenExpiredException(reason: String)
    : OnboardingException(reason, "ONBOARDING_TOKEN_EXPIRED")

class MerchantNotEligibleForLiveException(
    merchantId: String,
    status: MerchantStatus,
    reason: String
) : OnboardingException(
    "가맹점 '$merchantId'는 Live 전환 조건을 충족하지 않습니다: $reason (현재 상태: $status)",
    "MERCHANT_NOT_ELIGIBLE_FOR_LIVE"
)

class MerchantNotFoundException(merchantId: String)
    : OnboardingException("가맹점을 찾을 수 없습니다: $merchantId", "MERCHANT_NOT_FOUND")

class EnvironmentMismatchForOnboardingException(required: String, actual: String)
    : OnboardingException(
        "'$actual' 환경의 API Key는 '$required' 환경 전용 기능에 접근할 수 없습니다",
        "ENVIRONMENT_MISMATCH"
    )
