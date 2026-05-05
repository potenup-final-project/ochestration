package com.pg.ochestration.presentation.web.controller

import com.pg.ochestration.application.service.WebhookEndpointCreateResult
import com.pg.ochestration.application.service.WebhookEndpointService
import com.pg.ochestration.domain.model.ApiKeyEnvironment
import com.pg.ochestration.domain.model.WebhookEndpoint
import com.pg.ochestration.domain.model.WebhookEndpointStatus
import com.pg.ochestration.infrastructure.auth.MerchantPrincipal
import com.pg.ochestration.presentation.web.dto.CreateWebhookEndpointRequest
import com.pg.ochestration.presentation.web.dto.UpdateWebhookEndpointRequest
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebhookEndpointControllerTest {

    private val service: WebhookEndpointService = mock(WebhookEndpointService::class.java)
    private val controller = WebhookEndpointController(service)
    private val principal = MerchantPrincipal(merchantId = "merchant-001", environment = ApiKeyEnvironment.SANDBOX)
    private val objectMapper = ObjectMapper()

    @Test
    fun `create는 201과 signingSecret을 최초 1회 응답한다`() {
        val endpoint = anEndpoint()
        val result = WebhookEndpointCreateResult(endpoint = endpoint, signingSecret = "secret")
        `when`(service.create("merchant-001", "https://merchant.example/webhook", "결제 결과")).thenReturn(result)

        val response = controller.create(
            request = CreateWebhookEndpointRequest(url = "https://merchant.example/webhook", description = "결제 결과"),
            principal = principal
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("secret", response.body?.signingSecret)
        assertEquals(endpoint.endpointId, response.body?.endpointId)
    }

    @Test
    fun `list 응답에는 signingSecret이 포함되지 않는다`() {
        `when`(service.list("merchant-001")).thenReturn(listOf(anEndpoint()))

        val response = controller.list(principal)
        val json = objectMapper.writeValueAsString(response)

        assertFalse(json.contains("signingSecret"))
    }

    @Test
    fun `get 응답에는 signingSecret이 포함되지 않는다`() {
        `when`(service.get("merchant-001", "endpoint-001")).thenReturn(anEndpoint())

        val response = controller.get(endpointId = "endpoint-001", principal = principal)
        val json = objectMapper.writeValueAsString(response)

        assertFalse(json.contains("signingSecret"))
    }

    @Test
    fun `update는 principal merchantId로 서비스에 위임한다`() {
        `when`(
            service.update(
                merchantId = "merchant-001",
                endpointId = "endpoint-001",
                url = "https://merchant.example/updated",
                status = WebhookEndpointStatus.INACTIVE,
                description = "수정"
            )
        ).thenReturn(anEndpoint(status = WebhookEndpointStatus.INACTIVE, description = "수정"))

        controller.update(
            endpointId = "endpoint-001",
            request = updateRequest("""{"url":"https://merchant.example/updated","status":"INACTIVE","description":"수정"}"""),
            principal = principal
        )

        verify(service).update(
            merchantId = "merchant-001",
            endpointId = "endpoint-001",
            url = "https://merchant.example/updated",
            status = WebhookEndpointStatus.INACTIVE,
            description = "수정"
        )
    }

    @Test
    fun `update는 description 필드가 없으면 null로 위임한다`() {
        `when`(
            service.update(
                merchantId = "merchant-001",
                endpointId = "endpoint-001",
                url = null,
                status = WebhookEndpointStatus.INACTIVE,
                description = null
            )
        ).thenReturn(anEndpoint(status = WebhookEndpointStatus.INACTIVE, description = "기존"))

        controller.update(
            endpointId = "endpoint-001",
            request = updateRequest("""{"status":"INACTIVE"}"""),
            principal = principal
        )

        verify(service).update(
            merchantId = "merchant-001",
            endpointId = "endpoint-001",
            url = null,
            status = WebhookEndpointStatus.INACTIVE,
            description = null
        )
    }

    @Test
    fun `update는 description null을 null로 위임한다`() {
        `when`(
            service.update(
                merchantId = "merchant-001",
                endpointId = "endpoint-001",
                url = null,
                status = null,
                description = null
            )
        ).thenReturn(anEndpoint(description = "기존"))

        controller.update(
            endpointId = "endpoint-001",
            request = updateRequest("""{"description":null}"""),
            principal = principal
        )

        verify(service).update(
            merchantId = "merchant-001",
            endpointId = "endpoint-001",
            url = null,
            status = null,
            description = null
        )
    }

    @Test
    fun `delete는 물리 삭제 대신 비활성화 서비스에 위임한다`() {
        `when`(service.deactivate("merchant-001", "endpoint-001"))
            .thenReturn(anEndpoint(status = WebhookEndpointStatus.INACTIVE))

        val response = controller.delete(endpointId = "endpoint-001", principal = principal)

        assertEquals(WebhookEndpointStatus.INACTIVE, response.status)
        verify(service).deactivate("merchant-001", "endpoint-001")
    }

    private fun updateRequest(json: String): UpdateWebhookEndpointRequest =
        objectMapper.readValue(json, UpdateWebhookEndpointRequest::class.java)
}

private fun anEndpoint(
    status: WebhookEndpointStatus = WebhookEndpointStatus.ACTIVE,
    description: String? = null
) = WebhookEndpoint(
    endpointId = "endpoint-001",
    merchantId = "merchant-001",
    url = "https://merchant.example/webhook",
    signingSecret = "secret",
    status = status,
    description = description,
    createdAt = Instant.parse("2026-05-04T00:00:00Z"),
    updatedAt = Instant.parse("2026-05-04T00:00:00Z")
)
