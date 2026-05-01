package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.PaymentNotCancelableException
import com.pg.ochestration.domain.exception.PaymentNotFailableException

enum class PaymentStatus {
    READY,
    APPROVED,
    FAILED,
    CANCELED;

    fun ensureCancelable(paymentId: String) {
        if (this != APPROVED) throw PaymentNotCancelableException(paymentId, this)
    }

    // APPROVED 상태의 결제가 취소 처리 중 PG 오류로 실패하는 경우에만 허용
    fun ensureCanFail(paymentId: String) {
        if (this != APPROVED) throw PaymentNotFailableException(paymentId, this)
    }
}
