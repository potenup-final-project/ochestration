package com.pg.ochestration.application.port.out

import com.pg.ochestration.domain.model.Payment

interface PaymentSavePort {
    fun save(payment: Payment): Payment
    fun findById(paymentId: String): Payment?
}
