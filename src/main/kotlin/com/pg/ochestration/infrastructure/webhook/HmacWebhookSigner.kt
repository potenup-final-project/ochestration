package com.pg.ochestration.infrastructure.webhook

import com.pg.ochestration.application.port.out.WebhookSigner
import org.springframework.stereotype.Component
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class HmacWebhookSigner : WebhookSigner {

    override fun sign(secret: String, eventId: String, payload: String): String {
        val mac = Mac.getInstance(HMAC_SHA256)
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_SHA256)
        mac.init(key)
        val message = "$eventId.$payload"
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        const val HMAC_SHA256 = "HmacSHA256"
    }
}
