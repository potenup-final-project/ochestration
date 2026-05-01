package com.pg.ochestration.domain.exception

import com.pg.ochestration.domain.model.PaymentStatus

sealed class PaymentException(
    message: String,
    val errorCode: String
) : RuntimeException(message)

class PaymentNotFoundException(paymentId: String)
    : PaymentException("결제를 찾을 수 없습니다: $paymentId", "PAYMENT_NOT_FOUND")

class PaymentNotCancelableException(paymentId: String, currentStatus: PaymentStatus)
    : PaymentException(
        "취소할 수 없는 결제 상태입니다 — paymentId=$paymentId, status=$currentStatus",
        "PAYMENT_NOT_CANCELABLE"
    )

class PaymentNotFailableException(paymentId: String, currentStatus: PaymentStatus)
    : PaymentException(
        "실패 처리할 수 없는 결제 상태입니다 — paymentId=$paymentId, status=$currentStatus",
        "PAYMENT_NOT_FAILABLE"
    )
