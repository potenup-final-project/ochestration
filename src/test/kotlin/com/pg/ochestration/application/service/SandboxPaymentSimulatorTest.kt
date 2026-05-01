package com.pg.ochestration.application.service

import com.pg.ochestration.application.orchestration.ApprovePaymentCommand
import com.pg.ochestration.application.port.out.PaymentSavePort
import com.pg.ochestration.domain.model.Payment
import com.pg.ochestration.domain.model.PaymentStatus
import com.pg.ochestration.domain.model.Provider
import com.pg.ochestration.domain.model.SelectionSummary
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class SandboxPaymentSimulatorTest {

    private val fakePaymentSavePort = FakePaymentSavePort()
    private val simulator = SandboxPaymentSimulator(fakePaymentSavePort)

    @Test
    fun `orderId에 fail이 없으면 APPROVED 상태의 결제를 반환한다`() {
        val result = simulator.simulateApprove(aCommand(orderId = "order-normal-001"))

        assertEquals(PaymentStatus.APPROVED, result.status)
        assertEquals(Provider.TOSS, result.approvedProvider)
    }

    @Test
    fun `orderId에 fail이 포함되면 FAILED 상태의 결제를 반환한다`() {
        val result = simulator.simulateApprove(aCommand(orderId = "order-fail-001"))

        assertEquals(PaymentStatus.FAILED, result.status)
        assertEquals("SANDBOX_FORCED_FAILURE", result.failureCode)
    }

    @Test
    fun `orderId에 FAIL이 대문자로 포함되어도 FAILED 상태를 반환한다`() {
        val result = simulator.simulateApprove(aCommand(orderId = "order-FAIL-upper"))

        assertEquals(PaymentStatus.FAILED, result.status)
    }

    @Test
    fun `simulateCancel은 항상 CANCELED 상태를 반환한다`() {
        val payment = anApprovedPayment()

        val result = simulator.simulateCancel(payment)

        assertEquals(PaymentStatus.CANCELED, result.status)
        assertEquals(payment.paymentId, result.paymentId)
    }

    private fun aCommand(
        merchantId: String = "merchant-test",
        orderId: String = "order-001"
    ) = ApprovePaymentCommand(
        merchantId = merchantId,
        orderId = orderId,
        amount = 10000L,
        currency = "KRW",
        idempotencyKey = "idem-001",
        requestedAt = Instant.now(),
        preferredPrimaryProvider = null,
        metadata = emptyMap()
    )

    private fun anApprovedPayment(): Payment {
        val now = Instant.now()
        return Payment(
            paymentId = "pay_sandbox_test",
            merchantId = "merchant-test",
            orderId = "order-001",
            amount = 10000L,
            currency = "KRW",
            idempotencyKey = "idem-001",
            requestedAt = now,
            status = PaymentStatus.APPROVED,
            approvedProvider = Provider.TOSS,
            providerTxId = "sandbox_tx_test",
            approvedAt = now,
            attempts = emptyList(),
            selectionSummary = SelectionSummary(
                initialCandidates = emptyList(),
                filteredOutProviders = emptyList(),
                selectedPrimaryProvider = Provider.TOSS,
                selectedPrimaryReason = "테스트",
                fallbackReason = null,
                finalApprovedProvider = Provider.TOSS
            ),
            metadata = emptyMap()
        )
    }
}

// ---------------------------------------------------------------------------
// Fake 구현체
// ---------------------------------------------------------------------------

private class FakePaymentSavePort : PaymentSavePort {
    private val store: MutableMap<String, Payment> = mutableMapOf()

    override fun save(payment: Payment): Payment {
        store[payment.paymentId] = payment
        return payment
    }

    override fun findById(paymentId: String): Payment? = store[paymentId]
}
