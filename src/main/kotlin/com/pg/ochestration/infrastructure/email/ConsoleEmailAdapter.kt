package com.pg.ochestration.infrastructure.email

import com.pg.ochestration.application.port.out.EmailPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ConsoleEmailAdapter : EmailPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendEmailVerification(email: String, token: String, merchantId: String) {
        log.info(
            "[개발 이메일] 이메일 인증 토큰 발송 — email={}, token={}, merchantId={}",
            email, token, merchantId
        )
    }
}
