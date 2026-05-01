package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.PaymentNotCancelableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentStatusTest {

    @Test
    fun `APPROVED 상태는 ensureCancelable 호출 시 예외를 던지지 않는다`() {
        PaymentStatus.APPROVED.ensureCancelable("pay-001")
    }

    @Test
    fun `FAILED 상태는 ensureCancelable 호출 시 PaymentNotCancelableException을 던진다`() {
        assertFailsWith<PaymentNotCancelableException> {
            PaymentStatus.FAILED.ensureCancelable("pay-001")
        }
    }

    @Test
    fun `CANCELED 상태는 ensureCancelable 호출 시 PaymentNotCancelableException을 던진다`() {
        assertFailsWith<PaymentNotCancelableException> {
            PaymentStatus.CANCELED.ensureCancelable("pay-001")
        }
    }

    @Test
    fun `READY 상태는 ensureCancelable 호출 시 PaymentNotCancelableException을 던진다`() {
        assertFailsWith<PaymentNotCancelableException> {
            PaymentStatus.READY.ensureCancelable("pay-001")
        }
    }

    @Test
    fun `PaymentNotCancelableException은 PAYMENT_NOT_CANCELABLE errorCode를 가진다`() {
        val ex = assertFailsWith<PaymentNotCancelableException> {
            PaymentStatus.FAILED.ensureCancelable("pay-001")
        }
        assertEquals("PAYMENT_NOT_CANCELABLE", ex.errorCode)
    }

    @Test
    fun `PaymentNotCancelableException 메시지에 paymentId와 status가 포함된다`() {
        val ex = assertFailsWith<PaymentNotCancelableException> {
            PaymentStatus.CANCELED.ensureCancelable("pay-abc")
        }
        assertEquals(true, ex.message?.contains("pay-abc"))
        assertEquals(true, ex.message?.contains("CANCELED"))
    }
}
