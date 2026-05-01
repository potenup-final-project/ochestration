package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.PaymentNotCancelableException
import com.pg.ochestration.domain.exception.PaymentNotFailableException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class PaymentMarkCanceledTest {

    @Test
    fun `APPROVED 결제에 markCanceled 호출 시 status가 CANCELED인 새 객체를 반환한다`() {
        val payment = anApprovedPayment()
        val now = Instant.now()

        val canceled = payment.markCanceled(canceledAt = now, reason = "고객 요청")

        assertEquals(PaymentStatus.CANCELED, canceled.status)
        assertEquals(now, canceled.canceledAt)
        assertEquals("고객 요청", canceled.cancelReason)
    }

    @Test
    fun `markCanceled는 원본 Payment 객체를 변경하지 않고 새 객체를 반환한다`() {
        val payment = anApprovedPayment()
        val now = Instant.now()

        val canceled = payment.markCanceled(canceledAt = now, reason = "고객 요청")

        assertNotSame(payment, canceled)
        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertNull(payment.canceledAt)
        assertNull(payment.cancelReason)
    }

    @Test
    fun `markCanceled 후에도 paymentId와 merchantId는 동일하다`() {
        val payment = anApprovedPayment(paymentId = "pay-123", merchantId = "merch-456")
        val now = Instant.now()

        val canceled = payment.markCanceled(canceledAt = now, reason = "테스트 취소")

        assertEquals("pay-123", canceled.paymentId)
        assertEquals("merch-456", canceled.merchantId)
    }

    @Test
    fun `markCanceled에 failure를 포함하면 failureCode가 설정된다`() {
        val payment = anApprovedPayment()
        val failure = PaymentFailure(
            code = "CANCEL_FAILED",
            category = FailureCategory.RETRYABLE_TECHNICAL,
            message = "PG 취소 요청 실패"
        )

        val canceled = payment.markCanceled(
            canceledAt = Instant.now(),
            reason = "PG 오류",
            failure = failure
        )

        assertEquals("CANCEL_FAILED", canceled.failureCode)
        assertEquals(FailureCategory.RETRYABLE_TECHNICAL, canceled.failureCategory)
    }

    @Test
    fun `ensureCancelable은 APPROVED 상태에서 예외를 던지지 않는다`() {
        val payment = anApprovedPayment()
        payment.ensureCancelable()
    }

    @Test
    fun `ensureCancelable은 FAILED 상태에서 PaymentNotCancelableException을 던진다`() {
        val payment = anApprovedPayment().markFailed(
            PaymentFailure(code = "ERR", category = FailureCategory.NON_RETRYABLE_BUSINESS, message = "실패")
        )
        assertFailsWith<PaymentNotCancelableException> {
            payment.ensureCancelable()
        }
    }

    @Test
    fun `ensureCancelable이 던지는 예외는 PAYMENT_NOT_CANCELABLE errorCode를 가진다`() {
        val payment = anApprovedPayment().markFailed(
            PaymentFailure(code = "ERR", category = FailureCategory.NON_RETRYABLE_BUSINESS, message = "실패")
        )
        val ex = assertFailsWith<PaymentNotCancelableException> {
            payment.ensureCancelable()
        }
        assertEquals("PAYMENT_NOT_CANCELABLE", ex.errorCode)
    }

    @Test
    fun `Payment equals는 paymentId 기반으로 동작한다`() {
        val payment1 = anApprovedPayment(paymentId = "pay-001")
        val payment2 = anApprovedPayment(paymentId = "pay-001")
        val payment3 = anApprovedPayment(paymentId = "pay-002")

        assertEquals(payment1, payment2)
        assertEquals(false, payment1 == payment3)
    }

    @Test
    fun `Payment hashCode는 paymentId 기반으로 동작한다`() {
        val payment1 = anApprovedPayment(paymentId = "pay-001")
        val payment2 = anApprovedPayment(paymentId = "pay-001")

        assertEquals(payment1.hashCode(), payment2.hashCode())
    }

    @Test
    fun `markCanceled는 내부에서 상태를 검증하여 FAILED 결제에 호출 시 예외를 던진다`() {
        val failed = anApprovedPayment().markFailed(
            PaymentFailure(code = "ERR", category = FailureCategory.NON_RETRYABLE_BUSINESS, message = "실패")
        )
        assertFailsWith<PaymentNotCancelableException> {
            failed.markCanceled(canceledAt = Instant.now(), reason = "시도")
        }
    }

    @Test
    fun `markCanceled는 내부에서 상태를 검증하여 CANCELED 결제에 호출 시 예외를 던진다`() {
        val canceled = anApprovedPayment().markCanceled(canceledAt = Instant.now(), reason = "1차 취소")
        assertFailsWith<PaymentNotCancelableException> {
            canceled.markCanceled(canceledAt = Instant.now(), reason = "2차 취소 시도")
        }
    }

    @Test
    fun `markFailed는 APPROVED 상태에서만 허용된다`() {
        val payment = anApprovedPayment()
        val failure = PaymentFailure(code = "ERR", category = FailureCategory.RETRYABLE_TECHNICAL, message = "오류")

        val failed = payment.markFailed(failure)

        assertEquals(PaymentStatus.FAILED, failed.status)
    }

    @Test
    fun `markFailed는 CANCELED 결제에 호출 시 PaymentNotFailableException을 던진다`() {
        val canceled = anApprovedPayment().markCanceled(canceledAt = Instant.now(), reason = "취소됨")
        assertFailsWith<PaymentNotFailableException> {
            canceled.markFailed(PaymentFailure(code = "ERR", category = FailureCategory.RETRYABLE_TECHNICAL, message = "오류"))
        }
    }

    @Test
    fun `markFailed가 던지는 예외는 PAYMENT_NOT_FAILABLE errorCode를 가진다`() {
        val canceled = anApprovedPayment().markCanceled(canceledAt = Instant.now(), reason = "취소됨")
        val ex = assertFailsWith<PaymentNotFailableException> {
            canceled.markFailed(null)
        }
        assertEquals("PAYMENT_NOT_FAILABLE", ex.errorCode)
    }
}

// -------------------------------------------------------------------------
// Fixture
// -------------------------------------------------------------------------

private fun anApprovedPayment(
    paymentId: String = "pay-test-001",
    merchantId: String = "merchant-001"
) = Payment(
    paymentId = paymentId,
    merchantId = merchantId,
    orderId = "order-001",
    amount = 10_000L,
    currency = "KRW",
    idempotencyKey = "idem-001",
    requestedAt = Instant.now(),
    status = PaymentStatus.APPROVED,
    approvedProvider = Provider.TOSS,
    providerTxId = "toss-tx-001",
    approvedAt = Instant.now(),
    canceledAt = null,
    failureCode = null,
    failureCategory = null,
    failureMessage = null,
    attempts = emptyList(),
    selectionSummary = SelectionSummary.sandbox(),
    metadata = emptyMap(),
    cancelReason = null
)
