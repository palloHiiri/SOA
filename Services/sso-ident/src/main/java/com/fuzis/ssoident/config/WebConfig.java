package com.fuzis.ssoident.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebConfig implements WebFluxConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/sso-ident/**")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowCredentials(true)
        .allowedOrigins(
        "http://localhost:3000",
        "http://localhost:5173",
        "https://localhost:8443",
        "https://127.0.0.1:8443",
        "https://http://157.22.189.188:8443"
        );
    }
}
