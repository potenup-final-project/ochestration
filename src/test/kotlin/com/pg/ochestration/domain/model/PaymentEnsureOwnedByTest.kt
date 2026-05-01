package com.pg.ochestration.domain.model

import com.pg.ochestration.domain.exception.PaymentAccessDeniedException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentEnsureOwnedByTest {

    @Test
    fun `should not throw when merchantId matches payment owner`() {
        val payment = aPayment(merchantId = "merchant-001")
        payment.ensureOwnedBy("merchant-001")
    }

    @Test
    fun `should throw PaymentAccessDeniedException when merchantId does not match`() {
        val payment = aPayment(merchantId = "merchant-001")
        assertFailsWith<PaymentAccessDeniedException> {
            payment.ensureOwnedBy("merchant-999")
        }
    }

    @Test
    fun `should throw with PAYMENT_ACCESS_DENIED errorCode when merchantId does not match`() {
        val payment = aPayment(merchantId = "merchant-001")
        val ex = assertFailsWith<PaymentAccessDeniedException> {
            payment.ensureOwnedBy("merchant-intruder")
        }
        assertEquals("PAYMENT_ACCESS_DENIED", ex.errorCode)
    }
}

// -------------------------------------------------------------------------
// Fixture
// -------------------------------------------------------------------------

private fun aPayment(
    paymentId: String = "payment-001",
    merchantId: String = "merchant-001",
    status: PaymentStatus = PaymentStatus.APPROVED,
) = Payment(
    paymentId = paymentId,
    merchantId = merchantId,
    orderId = "order-001",
    amount = 10_000L,
    currency = "KRW",
    idempotencyKey = "idem-001",
    requestedAt = Instant.now(),
    status = status,
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
