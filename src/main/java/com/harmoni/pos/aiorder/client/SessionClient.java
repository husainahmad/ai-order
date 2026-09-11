package com.harmoni.pos.aiorder.client;

import com.harmoni.pos.aiorder.dto.CustomerSessionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * HTTP client for the customer backend session endpoints.
 * <p>
 * Opens a new customer session so chat messages have a host to attach to.
 * The source label tells the customer service where the session came from.
 *
 * @author Husain Harmoni
 */
@Slf4j
@Component
public class SessionClient {

    private final WebClient webClient;
    private final CustomerApiProperties properties;

    /**
     * Creates a session client bound to the customer-backend WebClient.
     *
     * @param webClient  the customer-backend WebClient
     * @param properties the customer API configuration (paths and source label)
     */
    public SessionClient(
            @Qualifier("customerWebClient") WebClient webClient,
            CustomerApiProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    /**
     * Creates a new customer session for the given customer.
     *
     * @param customerId the customer's numeric id
     * @return the newly created session response
     */
    public CustomerSessionResponse createSession(Long customerId) {
        log.debug("POST {} customerId={}", properties.sessionsPath(), customerId);
        Map<String, Object> body = Map.of("customerId", customerId, "source", properties.source());
        return webClient.post()
                .uri(properties.sessionsPath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(CustomerSessionResponse.class)
                .block();
    }
}