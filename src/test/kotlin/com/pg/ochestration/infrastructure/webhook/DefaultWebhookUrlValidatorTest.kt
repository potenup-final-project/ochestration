package com.pg.ochestration.infrastructure.webhook

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultWebhookUrlValidatorTest {

    private val validator = DefaultWebhookUrlValidator { host ->
        when (host) {
            "public.example" -> arrayOf(InetAddress.getByName("93.184.216.34"))
            "private.example" -> arrayOf(InetAddress.getByName("10.0.0.1"))
            else -> InetAddress.getAllByName(host)
        }
    }

    @Test
    fun `http와 https URL은 허용한다`() {
        assertTrue(validator.validate("https://public.example/webhook").allowed)
        assertTrue(validator.validate("http://public.example/webhook").allowed)
    }

    @Test
    fun `scheme 대소문자는 구분하지 않는다`() {
        assertTrue(validator.validate("HTTPS://public.example/webhook").allowed)
    }

    @Test
    fun `http와 https가 아닌 scheme은 거절한다`() {
        assertFalse(validator.validate("file:///etc/passwd").allowed)
    }

    @Test
    fun `userinfo와 fragment가 포함된 URL은 거절한다`() {
        assertFalse(validator.validate("https://user:pass@example.com/webhook").allowed)
        assertFalse(validator.validate("https://example.com/webhook#token").allowed)
    }

    @Test
    fun `localhost와 loopback 주소는 거절한다`() {
        assertFalse(validator.validate("http://localhost/webhook").allowed)
        assertFalse(validator.validate("http://127.0.0.1/webhook").allowed)
        assertFalse(validator.validate("http://[::1]/webhook").allowed)
    }

    @Test
    fun `private link local metadata 주소는 거절한다`() {
        assertFalse(validator.validate("http://10.0.0.1/webhook").allowed)
        assertFalse(validator.validate("http://172.16.0.1/webhook").allowed)
        assertFalse(validator.validate("http://192.168.0.1/webhook").allowed)
        assertFalse(validator.validate("http://169.254.169.254/latest/meta-data").allowed)
        assertFalse(validator.validate("http://[fe80::1]/webhook").allowed)
        assertFalse(validator.validate("http://[fc00::1]/webhook").allowed)
    }

    @Test
    fun `도메인이 private 주소로 해석되면 거절한다`() {
        assertFalse(validator.validate("https://private.example/webhook").allowed)
    }
}
