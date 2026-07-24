package com.eduagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    public WebConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns(
                    "/api/social/**",
                    "/api/auth/me",
                    "/api/auth/profile",
                    "/api/goals/**",
                    "/api/knowledge/**",
                    "/api/agents/**",
                    "/api/profile/**",
                    "/api/chat/**",
                    "/api/users/**",
                    "/api/resources/**",
                    "/api/conversations/**",
                    "/api/daily/**",
                    "/api/push/**"
                )
                .excludePathPatterns(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/social/group/all",
                    "/api/social/group/*/resources",
                    "/api/knowledge/bases",
                    "/api/knowledge/entry/*",
                    "/api/agents",
                    "/api/agents/enabled",
                    "/api/profile/extract",
                    "/api/users/leaderboard",
                    "/api/resources",
                    "/api/resources/featured",
                    "/api/resources/top",
                    "/api/resources/latest",
                    "/api/resources/category/*"
                );
    }
}