package com.pg.ochestration.domain.model

enum class SelectionPrimaryReason {
    /** 요청자가 선호 Provider를 지정했고 해당 Provider가 선택됨 */
    USER_PREFERRED,

    /** 선호 Provider가 제외되어 다음 우선순위 Provider가 선택됨 */
    USER_PREFERRED_EXCLUDED_FALLBACK,

    /** 선호 지정 없이 우선순위 기준 최상위 Provider 선택됨 */
    HIGHEST_PRIORITY_DEFAULT,

    /** 필터링 후 선택 가능한 Provider 없음 */
    NO_ELIGIBLE_PROVIDER
}
