package com.pg.ochestration.infrastructure.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class ApiKeyHasher(@Value("\${auth.api-key.pepper}") private val pepper: String) {

    init {
        require(pepper.length >= 32) {
            "AUTH_API_KEY_PEPPER 환경변수가 32자 미만입니다. 최소 32자 이상의 무작위 문자열을 설정하세요."
        }
    }

    fun hash(rawKey: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pepper.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(rawKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
