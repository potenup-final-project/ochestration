package com.pg.ochestration.infrastructure.config

import com.pg.ochestration.infrastructure.auth.ApiKeyAuthInterceptor
import com.pg.ochestration.infrastructure.auth.MerchantPrincipalArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class AuthWebMvcConfigurer(
    private val apiKeyAuthInterceptor: ApiKeyAuthInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(apiKeyAuthInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/auth/**", "/actuator/**")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(MerchantPrincipalArgumentResolver())
    }
}
