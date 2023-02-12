package ru.franq.apigateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        var cookies = request.getCookies();
        return webClientBuilder.build().get()
                .uri("http://localhost:9900/auth/whoiam")
                .retrieve().bodyToMono(String.class)
                .map(response -> {
                    log.info(response);
                    return exchange;
                })
                .flatMap(chain::filter).onErrorResume(error -> {
                    log.info("Error Happened");
                    HttpStatusCode errorCode = null;
                    byte[] body = null;
                    String errorMsg = "";
                    if (error instanceof WebClientResponseException) {
                        WebClientResponseException webCLientException = (WebClientResponseException) error;
                        body = ((WebClientResponseException) error).getResponseBodyAsByteArray();
                        errorCode = webCLientException.getStatusCode();
                        errorMsg = webCLientException.getStatusText();

                    } else {
                        errorCode = HttpStatus.BAD_GATEWAY;
                        errorMsg = HttpStatus.BAD_GATEWAY.getReasonPhrase();
                    }
//                            AuthorizationFilter.AUTH_FAILED_CODE
                    return onError(exchange, String.valueOf(errorCode.value()), errorMsg, body, errorCode);
                });
    }

    private Mono<Void> onError(ServerWebExchange exchange, String errCode, String err, byte[] errDetails, HttpStatusCode httpStatus) {
        DataBufferFactory dataBufferFactory = exchange.getResponse().bufferFactory();
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().add("Content-Type", "application/json");
        return response.writeWith(Mono.just(errDetails).map(t -> dataBufferFactory.wrap(t)));
    }

}
