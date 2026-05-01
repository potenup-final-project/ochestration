package com.pg.ochestration.infrastructure.persistence.jpa

import com.pg.ochestration.application.port.out.PaymentIdGeneratorPort
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UuidPaymentIdGenerator : PaymentIdGeneratorPort {
    override fun generate(): String = UUID.randomUUID().toString()
}
