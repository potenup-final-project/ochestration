package com.pg.ochestration.application.port.out

interface WebhookSigner {
    fun sign(secret: String, eventId: String, payload: String): String
}
