package com.harmoni.pos.aiorder.ui;

import com.harmoni.pos.aiorder.dto.CustomerResponse;
import com.harmoni.pos.aiorder.service.CustomerService;
import com.harmoni.pos.aiorder.service.OrderingService;
import com.harmoni.pos.aiorder.ui.component.*;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Main chat view for ordering: a header, a scrollable conversation, and a
 * message composer at the bottom.
 * <p>
 * Streaming replies are rendered incrementally via the {@link TypingIndicator}
 * and assistant bubbles. If no customer is linked to the {@link VaadinSession},
 * the {@link CustomerGateDialog} blocks the chat until the customer registers.
 *
 * @author Husain Harmoni
 */
@Route(value = "order", layout = MainLayout.class)
@RequiredArgsConstructor
public class OrderView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger log = LoggerFactory.getLogger(OrderView.class);
    private static final Pattern LEAKED_TOOL_CALL = Pattern.compile("\\{\\s*\"name\"\\s*:\\s*\"[^\"]+\"\\s*,\\s*\"arguments\"\\s*:\\s*\\{[^}]*\\}\\s*\\}");
    private static final Pattern TOOL_CALL_SEGMENT = Pattern.compile("(?s)<\\|start\\|>.*?<\\|call\\|>");
    private static final Pattern FINAL_TEXT = Pattern.compile("(?s)^.*?<\\|start\\|>assistant<\\|channel\\|>final<\\|message\\|>(.*)$");

    private final OrderingService orderingService;
    private final CustomerService customerService;
    private final Environment environment;
    private final Validator validator;

    private String sessionId;
    private Header header;
    private Scroller chatScroller;
    private VerticalLayout chatContainer;
    private ChatInput chatInput;
    private TypingIndicator typingIndicator;

    {
        addClassName("order-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        chatContainer = new VerticalLayout();
        chatContainer.addClassName("chat-container");
        chatContainer.setWidthFull();
        chatContainer.setPadding(true);
        chatContainer.setSpacing(true);

        chatScroller = new Scroller(chatContainer);
        chatScroller.addClassName("chat-scroller");
        chatScroller.setWidthFull();
        chatScroller.getStyle().set("flex", "1").set("overflow-y", "auto").set("overflow-x", "hidden");
        add(chatScroller);
        setFlexGrow(1, chatScroller);

        chatInput = new ChatInput();
        chatInput.setSendListener(this::handleUserMessage);
        add(chatInput);

        typingIndicator = new TypingIndicator();
        typingIndicator.setVisible(false);
    }

    /**
     * Sets up the view per navigation: ensures the header exists, ensures a
     * {@code sessionId} query parameter is present, and either shows the
     * customer gate or resumes the conversation for the linked customer.
     *
     * @param event the navigation event
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (header == null) {
            header = new Header(environment.getProperty("app.store-name", "Kopi Harmoni"));
            addComponentAtIndex(0, header);
        }

        Location location = event.getLocation();
        sessionId = location.getQueryParameters().getParameters().getOrDefault("sessionId", List.of()).stream().findFirst().orElse(null);

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
            event.forwardTo("order?sessionId=" + sessionId);
            return;
        }

        if (!customerService.exists(sessionId)) {
            chatContainer.removeAll();
            chatInput.setEnabled(false);
            showCustomerGate();
        } else {
            customerService.getCustomerResponse(sessionId).ifPresent(cr -> {
                VaadinSession.getCurrent().setAttribute("customerId", cr.id());
                VaadinSession.getCurrent().setAttribute("customerResponse", cr);
                chatContainer.removeAll();
                loadConversation(cr.name());
            });
            chatInput.setEnabled(true);
            chatInput.focus();
        }
    }

    /**
     * Opens the customer registration dialog to gate the chat.
     */
    private void showCustomerGate() {
        CustomerGateDialog dialog = new CustomerGateDialog(sessionId, customerService, validator, this::onCustomerRegistered);
        dialog.open();
    }

    /**
     * Callback invoked once a customer is resolved through the gate dialog.
     * Enables the composer, greets the customer, and binds the customer to the
     * current Vaadin session.
     *
     * @param customer the resolved customer
     */
    private void onCustomerRegistered(CustomerResponse customer) {
        chatInput.setEnabled(true);
        chatContainer.removeAll();
        VaadinSession.getCurrent().setAttribute("customerId", customer.id());
        VaadinSession.getCurrent().setAttribute("customerResponse", customer);
        loadConversation(customer.name());
        chatInput.focus();
    }

    /**
     * Loads a fresh conversation with a generic greeting.
     */
    private void loadConversation() {
        addAssistantMessage("Halo! Mau pesan apa hari ini? Silakan ketik pesanmu.");
    }

    /**
     * Loads a fresh conversation with a personalized greeting.
     *
     * @param customerName the customer's display name
     */
    private void loadConversation(String customerName) {
        if (customerName != null && !customerName.isBlank()) {
            addAssistantMessage("Halo " + customerName + "! Mau pesan apa hari ini? Silakan ketik pesanmu.");
        } else {
            loadConversation();
        }
    }

    /**
     * Handles a user-submitted message: appends the user bubble, shows the
     * typing indicator, and subscribes to the streaming assistant reply.
     * On stream error it falls back to a one-shot {@code sendMessage} call.
     *
     * @param message the user's message text
     */
    private void handleUserMessage(String message) {
        if (!customerService.exists(sessionId)) {
            showCustomerGate();
            return;
        }

        addUserMessage(message);
        chatInput.setWaitingForResponse(true);
        typingIndicator.setVisible(true);
        if (!typingIndicator.getParent().isPresent()) {
            chatContainer.add(typingIndicator);
        }
        scrollToBottom();

        String vaadinSessionId = sessionId;
        UI ui = UI.getCurrent();
        if (ui != null) ui.push();

        StringBuilder buffer = new StringBuilder();

        orderingService.streamMessage(vaadinSessionId, message)
                .subscribe(
                        chunk -> {
                            if (chunk == null || chunk.isBlank()) return;
                            buffer.append(chunk);
                            log.debug("Stream chunk vaadin={} token='{}' bufLen={}", vaadinSessionId, chunk.replace("\n","\\n"), buffer.length());
                        },
                        err -> {
                            String finalTextSnapshot = buffer.toString();
                            CompletableFuture.supplyAsync(() -> {
                                String fallbackText = null;
                                boolean isLeaked = isLeakedToolJson(finalTextSnapshot);
                                String finalTextForCheck = isLeaked ? "" : finalTextSnapshot;
                                if (finalTextForCheck.isBlank()) {
                                    try {
                                        var fallback = orderingService.sendMessage(vaadinSessionId, message);
                                        fallbackText = fallback.message();
                                    } catch (Exception ex) {
                                        fallbackText = "Maaf, AI sedang tidak tersedia: " + err.getMessage();
                                    }
                                }
                                return fallbackText;
                            }, Executors.newVirtualThreadPerTaskExecutor()).thenAccept(result -> ui.access(() -> {
                                log.error("Stream failed vaadin={}: {}", vaadinSessionId, err.getMessage(), err);
                                typingIndicator.setVisible(false);
                                if (typingIndicator.getParent().isPresent()) chatContainer.remove(typingIndicator);
                                String fallbackText = (String) result;
                                boolean isLeaked = isLeakedToolJson(finalTextSnapshot);
                                String finalText = isLeaked ? "" : finalTextSnapshot;
                                if (isLeaked) log.warn("Leaked tool JSON in stream error path, suppressing");
                                addAssistantMessage(finalText.isBlank() ? fallbackText : finalText);
                                chatInput.setWaitingForResponse(false);
                                scrollToBottom();
                                ui.push();
                            }));
                        },
                        () -> ui.access(() -> {
                            typingIndicator.setVisible(false);
                            if (typingIndicator.getParent().isPresent()) chatContainer.remove(typingIndicator);
                            String finalTextOrig = buffer.toString();
                            boolean isLeaked = isLeakedToolJson(finalTextOrig);
                            String finalText = isLeaked ? "" : finalTextOrig;
                            if (isLeaked) log.warn("Leaked tool JSON in stream complete path, suppressing");
                            addAssistantMessage(finalText.isBlank() ? "Maaf, tidak ada respons AI." : finalText);
                            chatInput.setWaitingForResponse(false);
                            scrollToBottom();
                            ui.push();
                        })
                );
    }

    /**
     * Appends a user chat bubble to the conversation.
     *
     * @param message the user's message text
     */
    private void addUserMessage(String message) {
        chatContainer.add(new UserMessage(message));
    }

    /**
     * Appends an assistant chat bubble, sanitizing the raw model transcript
     * before rendering: strips leaked tool-call JSON and control-token segments
     * and keeps only the final-channel text, then normalizes spacing.
     *
     * @param message the assistant's raw reply text
     */
    private void addAssistantMessage(String message) {
        if (message == null) return;
        message = sanitizeAssistantText(message);
        if (message.isBlank()) return;
        chatContainer.add(new AiMessage(message));
    }

    /**
     * Cleans the model's raw streamed text for display.
     * <p>
     * Removes Claude-style tool-call JSON ({@code {"name","arguments"}}), strips
     * control-token tool-call segments ({@code <|start|>...<|call|>}), keeps only
     * the text of a trailing {@code final} channel, and drops any remnant of a
     * {@code thinking} block.
     *
     * @param message the raw model text
     * @return the clean, user-facing text
     */
    private String sanitizeAssistantText(String message) {
        String clean = LEAKED_TOOL_CALL.matcher(message).replaceAll("").trim();
        clean = TOOL_CALL_SEGMENT.matcher(clean).replaceAll("");
        java.util.regex.Matcher finalText = FINAL_TEXT.matcher(clean);
        if (finalText.find()) clean = finalText.group(1);
        clean = clean.replaceAll("(?s)thinking.*?response", "").trim();
        return clean.replaceAll("([,;:])(?=[^\\s])", "$1 ").trim();
    }

    /**
     * Detects whether a partial reply leaked raw tool-call JSON to the UI.
     *
     * @param msg the reply text to inspect
     * @return {@code true} if the text looks like leaked tool-call JSON
     */
    private boolean isLeakedToolJson(String msg) {
        return msg != null && msg.trim().startsWith("{") && msg.contains("\"name\"") && msg.contains("\"arguments\"");
    }

    /**
     * Scrolls the chat scroller to the newest message via JavaScript.
     */
    private void scrollToBottom() {
        UI.getCurrent().getPage().executeJs("""
            var scroller = document.querySelector('.chat-scroller');
            if (scroller) {
                scroller.scrollTop = scroller.scrollHeight;
            }
        """);
    }
}