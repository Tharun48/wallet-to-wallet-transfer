package com.wallet.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
@Component
public class IdempotencyFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private static final String IDEMPOTENCY_HEADER = "idempotency-key";

    IdempotencyFilter(ReactiveStringRedisTemplate redisTemplate){
        this.redisTemplate=redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        // Intercept only mutation paths (POST requests for wallet creation or transfers)
        if ("POST".equalsIgnoreCase(method) && (path.contains("/wallet") || path.contains("/transfer"))) {

            List<String> headers = request.getHeaders().get(IDEMPOTENCY_HEADER);

            // Validate that the client actually provided an Idempotency-Key header
            if (headers == null || headers.isEmpty() || headers.get(0).trim().isEmpty()) {
                return setErrorResponse(exchange, HttpStatus.BAD_REQUEST, "Missing required Idempotency-Key header.");
            }

            String idempotencyKey = headers.get(0);

            // Construct a distinct tracking key for Redis isolation
            String redisKey = "idempotency:" + path + ":" + idempotencyKey;

            //Execute the non-blocking Redis check (SETNX equivalent) with a 10-minute window
            return redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "PROCESSING", Duration.ofMinutes(10))
                    .flatMap(isAbsent -> {
                        if (Boolean.TRUE.equals(isAbsent)) {
                            // Key did NOT exist -> First time seeing this request. Route to Wallet application.
                            return chain.filter(exchange);
                        } else {
                            // Key ALREADY exists -> Duplicate request caught. Reject immediately at the edge.
                            return setErrorResponse(exchange, HttpStatus.CONFLICT, "Duplicate request detected. Action is already processing or completed.");
                        }
                    });
        }

        // Pass-through for safe operations (GET balance, etc.) or unrelated routes
        return chain.filter(exchange);
    }

    private Mono<Void> setErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json");

        String jsonError = String.format("{\"error\": \"%s\", \"status\": %d}", message, status.value());
        byte[] bytes = jsonError.getBytes();

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
