package com.harmoni.pos.aiorder.service;

import com.harmoni.pos.aiorder.client.AiChatClient;
import com.harmoni.pos.aiorder.client.CartClient;
import com.harmoni.pos.aiorder.client.SessionClient;
import com.harmoni.pos.aiorder.dto.ChatResponse;
import com.vaadin.flow.server.VaadinSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAiOrderingService implements OrderingService {

    private final AiChatClient aiChatClient;
    private final SessionClient sessionClient;
    private final CustomerService customerService;
    private final CartClient cartClient;

    @Override
    public Long ensureSession(String vaadinSessionId) {
        return resolveCustomerSessionId(vaadinSessionId);
    }

    /**
     * Sends the message and blocks for the complete reply.
     *
     * @param vaadinSessionId the Vaadin session id
     * @param message         the user's message text
     * @return the assistant's reply, including any attachments
     */
    @Override
    public ChatResponse sendMessage(String vaadinSessionId, String message) {
        Long customerSessionId = resolveCustomerSessionId(vaadinSessionId);
        if (customerSessionId == null) {
            throw new IllegalStateException("Customer session not found for vaadinSessionId=" + vaadinSessionId);
        }
        var aiMsg = aiChatClient.chat(customerSessionId, message);
        String text = aiMsg != null && aiMsg.message() != null ? aiMsg.message() : "Maaf, tidak ada respons AI.";
        return new ChatResponse(text, List.of(), List.of(), List.of());
    }

    /**
     * Streams the assistant's reply for the given message.
     *
     * @param vaadinSessionId the Vaadin session id
     * @param message         the user's message text
     * @return a cold stream of text chunks; errors terminal with a failed signal
     */
    @Override
    public Flux<String> streamMessage(String vaadinSessionId, String message) {
        Long customerSessionId = resolveCustomerSessionId(vaadinSessionId);
        if (customerSessionId == null) {
            return Flux.error(new IllegalStateException("Customer session not found for vaadinSessionId=" + vaadinSessionId));
        }
        return aiChatClient.chatStream(customerSessionId, message)
                .doOnSubscribe(s -> log.info("Stream subscribe vaadin={} customerSessionId={}", vaadinSessionId, customerSessionId))
                .doOnError(e -> log.error("Stream error vaadin={}: {}", vaadinSessionId, e.getMessage(), e));
    }

    /**
     * Resolves the numeric customer-session id for the given Vaadin session,
     * lazily creating the backend session if needed.
     *
     * @param vaadinSessionId the Vaadin session id
     * @return the customer session id, or {@code null} when unavailable
     */
    private Long resolveCustomerSessionId(String vaadinSessionId) {
        VaadinSession vs = VaadinSession.getCurrent();
        if (vs == null) return null;

        Object storedSessionId = vs.getAttribute("customerSessionId");
        if (storedSessionId instanceof Long existingId) return existingId;

        Object storedCustomerId = vs.getAttribute("customerId");
        if (!(storedCustomerId instanceof Long customerId)) return null;

        var session = sessionClient.createSession(customerId);
        if (session != null && session.id() != null) {
            vs.setAttribute("customerSessionId", session.id());
            return session.id();
        }
        return null;
    }
}
