package com.seocho507.apigateway.config;

import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    private SpringCloudCircuitBreakerFilterFactory circuitBreakerGatewayFilterFactory;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("authentication_service_route", r -> r.path("api/v1/auth/**")
                        .filters(f -> f
                                .filter(circuitBreakerGatewayFilterFactory.apply(config -> {
                                    config.setName("authCircuitBreaker");
                                    config.setFallbackUri("forward:/fallback");
                                }))
                        )
                        .uri("http://authentication-service:8081"))
                .build();
    }
}
