package com.pg.ochestration.infrastructure.webhook

import com.pg.ochestration.application.port.out.WebhookUrlValidationResult
import com.pg.ochestration.application.port.out.WebhookUrlValidator
import org.springframework.stereotype.Component
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

@Component
class DefaultWebhookUrlValidator(
    private val resolveAllByName: (String) -> Array<InetAddress> = InetAddress::getAllByName
) : WebhookUrlValidator {

    override fun validate(url: String): WebhookUrlValidationResult {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: return WebhookUrlValidationResult.blocked("URL 형식이 올바르지 않습니다")

        val scheme = uri.scheme?.lowercase()
        if (scheme !in ALLOWED_SCHEMES) {
            return WebhookUrlValidationResult.blocked("웹훅 URL은 http 또는 https만 허용됩니다")
        }
        if (uri.rawUserInfo != null) {
            return WebhookUrlValidationResult.blocked("웹훅 URL에는 userinfo를 포함할 수 없습니다")
        }
        if (uri.rawFragment != null) {
            return WebhookUrlValidationResult.blocked("웹훅 URL에는 fragment를 포함할 수 없습니다")
        }

        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: return WebhookUrlValidationResult.blocked("웹훅 URL host가 없습니다")
        val asciiHost = runCatching { IDN.toASCII(host) }.getOrNull()
            ?: return WebhookUrlValidationResult.blocked("웹훅 URL host가 올바르지 않습니다")

        if (asciiHost.equals("localhost", ignoreCase = true)) {
            return WebhookUrlValidationResult.blocked("localhost 웹훅 URL은 허용되지 않습니다")
        }

        val addresses = runCatching { resolveAllByName(asciiHost) }.getOrNull()
            ?: return WebhookUrlValidationResult.blocked("웹훅 URL host를 해석할 수 없습니다")
        if (addresses.isEmpty()) {
            return WebhookUrlValidationResult.blocked("웹훅 URL host를 해석할 수 없습니다")
        }
        val blocked = addresses.firstOrNull(::isBlockedAddress)
            ?: return WebhookUrlValidationResult.allowed()

        return WebhookUrlValidationResult.blocked("내부망 또는 메타데이터 주소는 웹훅 URL로 허용되지 않습니다: ${blocked.hostAddress}")
    }

    private fun isBlockedAddress(address: InetAddress): Boolean =
        address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            isCloudMetadataAddress(address) ||
            isUniqueLocalIpv6(address)

    private fun isCloudMetadataAddress(address: InetAddress): Boolean =
        address is Inet4Address && address.hostAddress == CLOUD_METADATA_IPV4

    private fun isUniqueLocalIpv6(address: InetAddress): Boolean {
        if (address !is Inet6Address) return false
        val firstByte = address.address.first().toInt() and 0xff
        return firstByte and 0xfe == 0xfc
    }

    private companion object {
        val ALLOWED_SCHEMES = setOf("http", "https")
        const val CLOUD_METADATA_IPV4 = "169.254.169.254"
    }
}
