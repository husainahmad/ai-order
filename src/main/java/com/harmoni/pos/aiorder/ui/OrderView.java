package com.harmoni.pos.aiorder.ui;

import com.harmoni.pos.aiorder.service.OrderingService;
import com.harmoni.pos.aiorder.ui.component.*;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerResponse;
import com.harmoni.pos.customer.application.service.CustomerService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
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
 * The main AI ordering chat screen at {@code /order}.
 * <p>
 * Requires a valid {@code sessionId} query parameter and the customer gate to be
 * completed. Messages are streamed from the customer service via SSE and rendered as
 * chat bubbles. Leaked tool-call JSON and {@code <think>} blocks are stripped as a
 * safety net before display.
 * <p>
 * Layout structure (top to bottom):
 * <ol>
 *   <li>{@link Header} — sticky top bar with back button and store name</li>
 *   <li>{@link Scroller} wrapping a {@code chatContainer} — scrollable message list</li>
 *   <li>{@link ChatInput} — bottom message composer with Enter-to-send</li>
 * </ol>
 *
 * @author Husain Harmoni
 */
@Route(value = "order", layout = MainLayout.class)
@RequiredArgsConstructor
public class OrderView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger log = LoggerFactory.getLogger(OrderView.class);

    /**
     * Matches a leaked tool-call JSON object produced by the LLM, e.g.
     * {@code {"name":"getProductsByCategory","arguments":{...}}}.
     */
    private static final Pattern LEAKED_TOOL_CALL = Pattern.compile("\\{\\s*\"name\"\\s*:\\s*\"[^\"]+\"\\s*,\\s*\"arguments\"\\s*:\\s*\\{[^}]*\\}\\s*\\}");

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
    private Div welcomeState;

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
     * Pre-navigation hook that validates the session, resolves the customer,
     * and either loads the conversation or opens the customer gate.
     *
     * @param event the before-enter event carrying the {@code sessionId} query parameter
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

    /** Opens the customer gate dialog when the visitor has not registered yet. */
    private void showCustomerGate() {
        CustomerGateDialog dialog = new CustomerGateDialog(sessionId, customerService, validator, this::onCustomerRegistered);
        dialog.open();
    }

    /**
     * Callback after the customer gate succeeds. Enables the chat input,
     * stores customer data in the session, and loads the personalized greeting.
     *
     * @param customer the resolved customer response
     */
    private void onCustomerRegistered(CustomerResponse customer) {
        chatInput.setEnabled(true);
        chatContainer.removeAll();
        VaadinSession.getCurrent().setAttribute("customerId", customer.id());
        VaadinSession.getCurrent().setAttribute("customerResponse", customer);
        loadConversation(customer.name());
        chatInput.focus();
    }

    /** Displays a generic greeting when no customer context is available. */
    private void loadConversation() {
        addAssistantMessage("Halo! Mau pesan apa hari ini? Silakan ketik pesanmu.");
    }

    /**
     * Displays a personalized greeting using the customer's name.
     *
     * @param customerName the customer's display name; falls back to generic greeting if blank
     */
    private void loadConversation(String customerName) {
        if (customerName != null && !customerName.isBlank()) {
            addAssistantMessage("Halo " + customerName + "! Mau pesan apa hari ini? Silakan ketik pesanmu.");
        } else {
            loadConversation();
        }
    }

    /**
     * Shows a welcome state when the chat is empty (before greeting loads).
     */
    private void showWelcomeState() {
        hideWelcomeState();
        welcomeState = new Div();
        welcomeState.addClassName("chat-welcome");

        Div iconWrap = new Div();
        iconWrap.addClassName("chat-welcome-icon");
        iconWrap.add(new Icon(VaadinIcon.COFFEE));

        Span title = new Span("Kopi Harmoni");
        title.addClassName("chat-welcome-title");

        Span subtitle = new Span("Ketik pesan untuk mulai memesan");
        subtitle.addClassName("chat-welcome-subtitle");

        welcomeState.add(iconWrap, title, subtitle);
        chatContainer.add(welcomeState);
    }

    /** Removes the welcome state if present. */
    private void hideWelcomeState() {
        if (welcomeState != null && welcomeState.getParent().isPresent()) {
            chatContainer.remove(welcomeState);
        }
        welcomeState = null;
    }

    /**
     * Streams the user's message to the AI via SSE and renders the response.
     * <p>
     * Tokens are collected silently in a buffer; the full response is displayed as a
     * single bubble once the stream completes. On stream failure, falls back to a
     * blocking {@link OrderingService#sendMessage} call. Leaked tool JSON is suppressed.
     *
     * @param message the user's input text
     */
    private void handleUserMessage(String message) {
        if (!customerService.exists(sessionId)) {
            showCustomerGate();
            return;
        }

        hideWelcomeState();
        addUserMessage(message);
        chatInput.setWaitingForResponse(true);
        typingIndicator.setVisible(true);
        if (!isTypingIndicatorAttached()) {
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

    /** @return {@code true} if the typing indicator is currently attached to the chat container */
    private boolean isTypingIndicatorAttached() {
        return typingIndicator.getParent().isPresent();
    }

    /**
     * Appends a user chat bubble to the chat container.
     *
     * @param message the message text to display
     */
    private void addUserMessage(String message) {
        chatContainer.add(new UserMessage(message));
    }

    /**
     * Appends an assistant chat bubble to the chat container, stripping any leaked
     * tool-call JSON, {@code <think>} blocks, and adding spaces after punctuation.
     *
     * @param message the raw AI response text
     */
    private void addAssistantMessage(String message) {
        if (message == null) return;
        if (LEAKED_TOOL_CALL.matcher(message).find()) {
            message = LEAKED_TOOL_CALL.matcher(message).replaceAll("").trim();
        }
        message = message.replaceAll("(?s)<think>.*?</think>", "").trim();
        message = message.replaceAll("([,;:])(?=[^\\s])", "$1 ");
        chatContainer.add(new AiMessage(message));
    }

    /**
     * Checks whether the given message is a raw leaked tool-call JSON document.
     *
     * @param msg the message text to inspect
     * @return {@code true} if the message starts with {@code \{} and contains tool-call keys
     */
    private boolean isLeakedToolJson(String msg) {
        return msg != null && msg.trim().startsWith("{") && msg.contains("\"name\"") && msg.contains("\"arguments\"");
    }

    /** Executes client-side JavaScript to scroll the chat pane to the newest message. */
    private void scrollToBottom() {
        UI.getCurrent().getPage().executeJs("""
            var scroller = document.querySelector('.chat-scroller');
            if (scroller) {
                scroller.scrollTop = scroller.scrollHeight;
            }
        """);
    }
}
