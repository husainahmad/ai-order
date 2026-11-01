package com.harmoni.pos.aiorder.client;

import com.harmoni.pos.aiorder.cart.dto.AddCartItemRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartClient {

    @Qualifier("cartWebClient")
    private final WebClient webClient;
    private final CartApiProperties properties;

    public Map<String, Object> addItem(Long sessionId, AddCartItemRequest request) {
        log.debug("POST {} items sessionId={} customizations={}", 
                properties.getEndpoints().getBasePath(), sessionId, 
                request.getCustomizations() != null ? request.getCustomizations().size() : 0);

        String path = properties.getEndpoints().getBasePath() + "/" + sessionId + "/cart" + properties.getEndpoints().getAddItem();
        return webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>(){})
                .block();
    }

    public static class CartApiException extends RuntimeException {
        public CartApiException(String message) { super(message); }
        public CartApiException(String message, Throwable cause) { super(message, cause); }
    }
}
