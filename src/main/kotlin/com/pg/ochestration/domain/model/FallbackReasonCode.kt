package com.pg.ochestration.domain.model

enum class FallbackReasonCode {
    /** 필터링 후 후보 PG가 없음 */
    NO_PROVIDER_AVAILABLE,

    /** 기술적 실패로 다음 PG로 폴백 진행 중 */
    PROVIDER_TECHNICAL_FAILURE,

    /** 서킷브레이커 오픈으로 제외 */
    CIRCUIT_OPEN,

    /** Provider 상태 비정상으로 제외 */
    PROVIDER_UNHEALTHY,

    /** Provider 연결 해제 상태로 제외 */
    NOT_CONNECTED,

    /** 요청한 기능을 지원하지 않는 Provider로 제외 */
    CAPABILITY_NOT_SUPPORTED,

    /** NON_RETRYABLE_BUSINESS 실패로 폴백 중단 */
    NON_RETRYABLE_STOP,

    /** 재시도 가능 실패가 모든 후보에서 발생, 더 이상 폴백 없음 */
    ALL_PROVIDERS_EXHAUSTED
}
