package com.harmoni.pos.aiorder.client;

import com.harmoni.pos.aiorder.dto.CustomerMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * HTTP client for the customer backend AI chat endpoints.
 * <p>
 * Sends messages to the assistant running on the customer service and streams
 * the reply tokens as Server-Sent Events for a typewriter effect in the UI.
 *
 * @author Husain Harmoni
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiChatClient {

    @Qualifier("customerWebClient")
    private final WebClient webClient;

    /**
     * Sends a message to the AI assistant and returns the complete reply.
     *
     * @param sessionId the customer session id
     * @param message   the user's message text
     * @return the assistant's reply, or {@code null} if the backend returns nothing
     */
    public CustomerMessageResponse chat(Long sessionId, String message) {
        log.debug("POST /api/v1/customer-sessions/{}/chat", sessionId);
        Map<String, String> body = Map.of("message", message);
        return webClient.post()
                .uri("/api/v1/customer-sessions/{sessionId}/chat", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(CustomerMessageResponse.class)
                .block();
    }

    /**
     * Streams the assistant's reply tokens incrementally via SSE.
     *
     * @param sessionId the customer session id
     * @param message   the user's message text
     * @return a reactive stream of response text chunks
     */
    public Flux<String> chatStream(Long sessionId, String message) {
        log.debug("POST /api/v1/customer-sessions/{}/chat/stream", sessionId);
        Map<String, String> body = Map.of("message", message);
        return webClient.post()
                .uri("/api/v1/customer-sessions/{sessionId}/chat/stream", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class);
    }
}