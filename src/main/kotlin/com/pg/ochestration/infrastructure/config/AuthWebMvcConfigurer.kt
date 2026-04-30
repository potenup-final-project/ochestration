package com.pg.ochestration.infrastructure.config

import com.pg.ochestration.infrastructure.auth.ApiKeyAuthInterceptor
import com.pg.ochestration.infrastructure.auth.MerchantPrincipalArgumentResolver
import com.pg.ochestration.infrastructure.auth.OnboardingTokenInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class AuthWebMvcConfigurer(
    private val apiKeyAuthInterceptor: ApiKeyAuthInterceptor,
    private val onboardingTokenInterceptor: OnboardingTokenInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(apiKeyAuthInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/auth/**",
                "/api/onboarding/register",
                "/api/onboarding/verify-email",
                "/actuator/**"
            )

        registry.addInterceptor(onboardingTokenInterceptor)
            .addPathPatterns("/api/auth/keys")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(MerchantPrincipalArgumentResolver())
    }

    @Bean
    fun passwordEncoder(): BCryptPasswordEncoder = BCryptPasswordEncoder()
}
