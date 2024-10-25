package com.seocho507.apigateway.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seocho507.apigateway.config.CommonConstant;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class CachingFilter implements GlobalFilter, Ordered {

    private ReactiveStringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;

    public CachingFilter(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return -1; // 필터 순서를 가장 먼저 실행되도록 설정
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String cacheKey = generateCacheKey(exchange.getRequest());

        return redisTemplate.opsForValue().get(cacheKey).flatMap(cachedValue -> {
            // 캐시된 응답 반환
            return returnCachedResponse(exchange, cachedValue);
        }).switchIfEmpty(Mono.defer(() -> {
            // 캐시 없음, 백엔드로 요청 전달 및 응답 캐싱
            ServerHttpResponse response = exchange.getResponse();

            // 응답을 변형하기 위해 ServerHttpResponseDecorator 사용
            ResponseAdapter responseAdapter = new ResponseAdapter(response);

            return chain.filter(exchange.mutate().response(responseAdapter).build()).then(Mono.defer(() -> {
                Mono<String> bodyMono = responseAdapter.getBody();

                return bodyMono.flatMap(body -> {
                    // 응답을 캐시에 저장
                    int statusCode = response.getStatusCode().value();
                    Map<String, String> headers = response.getHeaders().toSingleValueMap();

                    return cacheResponse(cacheKey, statusCode, headers, body).then(Mono.defer(() -> {
                        // 원래 응답 반환
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        DataBuffer buffer = response.bufferFactory().wrap(bytes);
                        return response.writeWith(Mono.just(buffer));
                    }));
                });
            }));
        }));
    }

    private String generateCacheKey(ServerHttpRequest request) {
        //TODO : implemet  키 생성 로직 구현 (HTTP 메서드, URI, 쿼리 파라미터 등)
        return "api_cache:" + request.getURI().getPath();
    }

    private Mono<Boolean> cacheResponse(String cacheKey, int status, Map<String, String> headers, String body) {
        Map<String, Object> cacheValue = Map.of(CommonConstant.STATUS, status, CommonConstant.HEADERS, headers, CommonConstant.BODY, body);

        try {
            String serializedValue = objectMapper.writeValueAsString(cacheValue);

            return redisTemplate.opsForValue().set(cacheKey, serializedValue);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private Mono<Void> returnCachedResponse(ServerWebExchange exchange, String cachedValue) {
        try {
            Map<String, Object> cacheValue = objectMapper.readValue(cachedValue, new TypeReference<>() {
            });

            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.valueOf((Integer) cacheValue.get(CommonConstant.STATUS)));

            Map<String, String> headers = (Map<String, String>) cacheValue.get(CommonConstant.HEADERS);
            response.getHeaders().setAll(headers);

            byte[] bytes = ((String) cacheValue.get(CommonConstant.BODY)).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);

            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }
}