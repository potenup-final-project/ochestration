package com.pg.ochestration.infrastructure.webhook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HmacWebhookSignerTest {

    private val signer = HmacWebhookSigner()

    @Test
    fun `sign은 eventId와 payload를 점으로 연결해 HMAC SHA256 hex를 반환한다`() {
        val signature = signer.sign(
            secret = "secret",
            eventId = "event-001",
            payload = """{"eventId":"event-001"}"""
        )

        assertEquals("fce05ca029e19dd1cc828318370a5ced8b2a5104c02b03ebf1729ab25f7f5a91", signature)
    }

    @Test
    fun `payload가 같아도 eventId가 다르면 서명이 달라진다`() {
        val first = signer.sign(
            secret = "secret",
            eventId = "event-001",
            payload = """{"paymentId":"payment-001"}"""
        )
        val second = signer.sign(
            secret = "secret",
            eventId = "event-002",
            payload = """{"paymentId":"payment-001"}"""
        )

        assertNotEquals(first, second)
    }
}
