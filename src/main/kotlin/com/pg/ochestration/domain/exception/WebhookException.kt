package com.pg.ochestration.domain.exception

sealed class WebhookException(
    message: String,
    val errorCode: String
) : RuntimeException(message)

class WebhookEndpointNotFoundException(endpointId: String)
    : WebhookException("웹훅 엔드포인트를 찾을 수 없습니다: $endpointId", "WEBHOOK_ENDPOINT_NOT_FOUND")

class WebhookEndpointInactiveException(endpointId: String)
    : WebhookException("비활성화된 웹훅 엔드포인트입니다: $endpointId", "WEBHOOK_ENDPOINT_INACTIVE")

class WebhookEndpointLimitExceededException(merchantId: String, limit: Int)
    : WebhookException(
        "웹훅 엔드포인트 등록 한도를 초과했습니다: merchantId=$merchantId, limit=$limit",
        "WEBHOOK_ENDPOINT_LIMIT_EXCEEDED"
    )

class WebhookEndpointUrlNotAllowedException(reason: String)
    : WebhookException("허용되지 않는 웹훅 URL입니다: $reason", "WEBHOOK_ENDPOINT_URL_NOT_ALLOWED")

class WebhookEndpointDescriptionTooLongException(length: Int, limit: Int)
    : WebhookException(
        "웹훅 엔드포인트 설명은 ${limit}자를 초과할 수 없습니다: length=$length",
        "WEBHOOK_ENDPOINT_DESCRIPTION_TOO_LONG"
    )

class WebhookDeliveryNotRetryableException(deliveryId: String, status: String)
    : WebhookException(
        "재시도할 수 없는 웹훅 delivery 상태입니다: deliveryId=$deliveryId, status=$status",
        "WEBHOOK_DELIVERY_NOT_RETRYABLE"
    )
