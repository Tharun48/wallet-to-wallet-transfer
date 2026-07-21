package com.wallet.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
@Component
public class IdempotencyFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private static final String IDEMPOTENCY_HEADER = "idempotencyKey";
    private static final String PROCESSING = "PROCESSING";

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

            return redisTemplate.opsForValue().get(redisKey)
                    .flatMap(cachedValue -> {
                        if (PROCESSING.equals(cachedValue)) {
                            // 1. Still in progress -> Block duplicate execution with HTTP 409
                            return setErrorResponse(exchange, HttpStatus.CONFLICT, "Duplicate request in progress.");
                        } else {
                            // 2. Response ALREADY exists in Redis -> Return the saved JSON directly with HTTP 200!
                            return setSuccessResponse(exchange, cachedValue);
                        }
                    })
                    .switchIfEmpty(
                            // 3. Completely new request -> Lock with "PROCESSING" and forward downstream
                            redisTemplate.opsForValue()
                                    .setIfAbsent(redisKey, PROCESSING, Duration.ofMinutes(10))
                                    .flatMap(isAcquired -> {
                                        if (Boolean.TRUE.equals(isAcquired)) {
                                            return chain.filter(exchange); // Forward to Wallet Service
                                        } else {
                                            return setErrorResponse(exchange, HttpStatus.CONFLICT, "Duplicate request detected.");
                                        }
                                    })
                    );
        }

        // Pass-through for safe operations (GET balance, etc.) or unrelated routes
        return chain.filter(exchange);
    }

    private Mono<Void> setSuccessResponse(ServerWebExchange exchange, String jsonBody) {
        ServerHttpResponse response = exchange.getResponse();

        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
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
