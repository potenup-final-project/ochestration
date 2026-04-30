package com.pg.ochestration.application.port.out

interface EmailPort {
    fun sendEmailVerification(email: String, token: String, merchantId: String)
}
