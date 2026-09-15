package com.harmoni.pos.aiorder.service;

import com.harmoni.pos.aiorder.dto.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Entry point for sending chat messages to the AI ordering assistant.
 * <p>
 * Streams the assistant's reply token-by-token for a typewriter effect, and
 * provides a blocking variant for one-shot replies (e.g. fallback paths).
 */
@Service
public interface OrderingService {

    /**
     * Resolves the numeric customer-session id for the given Vaadin session,
     * lazily creating the backend session if needed.
     *
     * @param sessionId the Vaadin session id
     * @return the customer session id, or {@code null} when unavailable
     */
    Long ensureSession(String sessionId);

    /**
     * Streams the assistant's reply for the given message.
     *
     * @param sessionId the Vaadin session id
     * @param message   the user's message text
     * @return a cold stream of text chunks; errors terminal with a failed signal
     */
    Flux<String> streamMessage(String sessionId, String message);

    /**
     * Sends the message and blocks for the complete reply.
     *
     * @param sessionId the Vaadin session id
     * @param message   the user's message text
     * @return the assistant's reply, including any attachments
     */
    ChatResponse sendMessage(String sessionId, String message);
}