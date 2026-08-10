package com.example.gatewayservice.handler;

import com.example.gatewayservice.model.GatewayResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

@Component
public class GlobalGatewayExceptionHandler implements WebExceptionHandler, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GlobalGatewayExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GlobalGatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        ErrorBody body = resolveBody(ex);
        log.error("Gateway Exception -> code={}, message={}", body.code, body.message, ex);

        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().add("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(GatewayResponse.fail(body.code, body.message));
        } catch (Exception jsonEx) {
            String fallback = "{\"code\":500,\"message\":\"Gateway JSON serialize failed\",\"data\":null}";
            bytes = fallback.getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -2;
    }

    private ErrorBody resolveBody(Throwable ex) {
        if (ex instanceof NotFoundException) {
            return new ErrorBody(404, "No route found");
        }
        if (ex instanceof ResponseStatusException) {
            ResponseStatusException rse = (ResponseStatusException) ex;
            int code = rse.getStatus() != null ? rse.getStatus().value() : 500;
            String message = rse.getReason() != null ? rse.getReason() : rse.getMessage();
            return new ErrorBody(code, message);
        }
        if (ex instanceof TimeoutException) {
            return new ErrorBody(504, "Gateway timeout");
        }
        if (ex instanceof ConnectException) {
            return new ErrorBody(502, "Upstream connect failed");
        }
        return new ErrorBody(500, "Gateway internal error");
    }

    private static class ErrorBody {
        private final int code;
        private final String message;

        private ErrorBody(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}

