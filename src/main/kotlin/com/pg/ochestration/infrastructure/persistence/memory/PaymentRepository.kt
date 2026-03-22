package com.pg.ochestration.infrastructure.persistence.memory

import com.pg.ochestration.domain.model.Payment
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Repository
class PaymentRepository {
    private val store = ConcurrentHashMap<String, Payment>()
    private val sequence = AtomicInteger(1)

    fun nextPaymentId(): String = "pay-${sequence.getAndIncrement().toString().padStart(3, '0')}"

    fun save(payment: Payment): Payment {
        store[payment.paymentId] = payment
        return payment
    }

    fun findById(paymentId: String): Payment? = store[paymentId]
}
