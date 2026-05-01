package com.pg.ochestration.infrastructure.auth

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class MerchantPrincipalArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == MerchantPrincipal::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Any {
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java)!!
        return request.getAttribute(MerchantContext.ATTR_KEY) as? MerchantPrincipal
            ?: error("MerchantPrincipal이 요청 컨텍스트에 없습니다. ApiKeyAuthInterceptor가 등록되어 있고 auth.api-key.enabled=true인지 확인하세요.")
    }
}
