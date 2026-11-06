package com.harmoni.pos.aiorder.ui;

import com.harmoni.pos.aiorder.client.CategoryClient;
import com.harmoni.pos.aiorder.client.ProductClient;
import com.harmoni.pos.aiorder.dto.CategoryRecommendation;
import com.harmoni.pos.aiorder.dto.ProductRecommendation;
import com.harmoni.pos.aiorder.service.CustomerService;
import com.harmoni.pos.aiorder.service.OrderingService;
import com.harmoni.pos.aiorder.ui.component.*;
import com.harmoni.pos.aiorder.ui.support.AssistantMessageSanitizer;
import com.harmoni.pos.aiorder.ui.support.MenuChipFactory;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Main chat view for ordering: a header, a scrollable conversation, and a
 * message composer at the bottom.
 * <p>
 * Streaming replies are rendered incrementally via the {@link TypingIndicator}
 * and assistant bubbles. The chat is unusable until a customer is linked to the
 * {@link VaadinSession}; an unauthenticated visitor is sent to the landing page
 * to sign in.
 *
 * @author Husain Harmoni
 */
@Route(value = "order", layout = MainLayout.class)
@RequiredArgsConstructor
public class OrderView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger log = LoggerFactory.getLogger(OrderView.class);

    private static final int MAX_CATEGORY_CHIPS = 6;
    private static final String NO_RESPONSE_FALLBACK = "Maaf, tidak ada respons AI.";
    private static final String SERVICE_DOWN_FALLBACK = "Maaf, AI sedang tidak tersedia.";
    private static final String[] STREAM_QUICK_REPLIES =
            {"Lihat menu lagi", "Rekap pesanan", "Selesai / bayar"};

    private final OrderingService orderingService;
    private final CustomerService customerService;
    private final Environment environment;
    private final CategoryClient categoryClient;
    private final ProductClient productClient;

    private final ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private String sessionId;
    private Header header;
    private Scroller chatScroller;
    private VerticalLayout chatContainer;
    private ChatInput chatInput;
    private TypingIndicator typingIndicator;
    private List<CategoryRecommendation> currentCategories = List.of();

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
     * Sets up the view per navigation: ensures the header exists and resolves
     * the {@code sessionId} — preferring a legacy query parameter if present,
     * otherwise reusing the value stored in the {@link VaadinSession} (created
     * on first visit) so no identifying UUID appears in the URL. Then either
     * redirects to sign-in or resumes the conversation for the linked customer.
     *
     * @param event the navigation event
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (header == null) {
            header = new Header(environment.getProperty("app.store-name", "Kopi Harmoni"));
            addComponentAtIndex(0, header);
        }

        sessionId = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("sessionId", List.of()).stream().findFirst().orElse(null);

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = (String) VaadinSession.getCurrent().getAttribute("sessionId");
        }
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
            VaadinSession.getCurrent().setAttribute("sessionId", sessionId);
        }

        if (!customerService.exists(sessionId)) {
            chatContainer.removeAll();
            chatInput.setEnabled(false);
            redirectToSignIn();
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
     * Sends an unauthenticated visitor to the landing page to sign in.
     * <p>
     * Sign-in lives on the landing page, as the page itself rather than a layer over the
     * chat. Sending the visitor there keeps one implementation of the form and means the
     * chat never renders behind a modal it cannot control.
     */
    private void redirectToSignIn() {
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.navigate("");
        }
    }

    /**
     * Loads a fresh conversation with a generic greeting.
     */
    private void loadConversation() {
        attachCategoryChips(addAssistantMessage("Halo! Mau pesan apa hari ini? Silakan pilih menu di bawah atau ketik pesanmu."));
    }

    /**
     * Loads a fresh conversation with a personalized greeting.
     *
     * @param customerName the customer's display name
     */
    private void loadConversation(String customerName) {
        if (customerName != null && !customerName.isBlank()) {
            attachCategoryChips(addAssistantMessage("Halo " + customerName + "! Mau pesan apa hari ini? Silakan pilih menu di bawah atau ketik pesanmu."));
        } else {
            loadConversation();
        }
    }

    /**
     * Extends the greeting bubble with real menu categories fetched from the
     * customer backend. Falls back to generic starter chips when the fetch fails.
     *
     * @param greeting the greeting bubble to decorate, or {@code null} if none rendered
     */
    private void attachCategoryChips(AiMessage greeting) {
        if (greeting == null) {
            return;
        }
        loadFromBackend("menu categories", categoryClient::getCategories, categories -> {
            if (categories.isEmpty()) {
                withQuickReplies(greeting, "Lihat menu", "Menu kopi", "Menu non-kopi");
                return;
            }
            currentCategories = List.copyOf(categories);
            List<String> chips = categories.stream()
                    .map(CategoryRecommendation::name)
                    .limit(MAX_CATEGORY_CHIPS)
                    .toList();
            greeting.addQuickReplies(chips, this::onCategoryQuickReply);
            scrollToBottom();
        });
    }

    /**
     * Handles a tapped category chip: fills the composer with the category
     * name for a quick edit-and-send, and lists that category's products as
     * additional chips that also fill the composer when tapped.
     *
     * @param categoryName the tapped category name
     */
    private void onCategoryQuickReply(String categoryName) {
        if (chatInput.isWaitingForResponse()) {
            return;
        }
        fillInput(categoryName);

        int categoryId = currentCategories.stream()
                .filter(c -> c.name().equals(categoryName))
                .findFirst()
                .map(CategoryRecommendation::id)
                .orElse(-1);
        if (categoryId <= 0) {
            return;
        }

        loadFromBackend(
                "products for category '" + categoryName + "'",
                customerSessionId -> productClient.getByCategory(customerSessionId, categoryId),
                products -> {
                    if (products.isEmpty()) {
                        return;
                    }
                    AiMessage ai = addAssistantMessage(
                            "Menu **" + categoryName + "** — ketuk produk untuk mengisi pesan:");
                    if (ai != null) {
                        attachProductChips(ai, products);
                    }
                    scrollToBottom();
                });
    }

    /**
     * Runs a backend menu lookup off the request thread and hands the result to the UI.
     * <p>
     * Every menu lookup needs the same three things: a customer session, a call that may
     * fail, and a hop back to the UI thread. Collecting them here keeps each caller down to
     * the part that is actually different.
     *
     * @param what     the resource being loaded, for the failure log
     * @param loader   the backend call, given the resolved customer session id
     * @param onLoaded consumer invoked on the UI thread with the (possibly empty) result
     * @param <T>      the element type of the loaded list
     */
    private <T> void loadFromBackend(String what, Function<Long, List<T>> loader, Consumer<List<T>> onLoaded) {
        UI ui = UI.getCurrent();
        if (ui == null) {
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                Long customerSessionId = orderingService.ensureSession(sessionId);
                return customerSessionId == null ? List.<T>of() : loader.apply(customerSessionId);
            } catch (Exception ex) {
                log.warn("Failed to load {}: {}", what, ex.getMessage());
                return List.<T>of();
            }
        }, ioExecutor).thenAccept(items -> ui.access(() -> onLoaded.accept(items)));
    }

    /**
     * Adds product chips to an assistant bubble. Each chip drafts an order intent,
     * including the product's customizations when it has any.
     *
     * @param ai       the bubble to decorate
     * @param products the products to render as chips
     */
    private void attachProductChips(AiMessage ai, List<ProductRecommendation> products) {
        ai.addQuickRepliesWithPayloads(MenuChipFactory.productReplies(products), this::fillInput);
    }

    /**
     * Fills the chat composer with the given text so the customer can review
     * or edit it before sending.
     *
     * @param text the draft message text
     */
    private void fillInput(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        chatInput.fill(text);
        scrollToBottom();
    }

    /**
     * Attaches quick-reply suggestion chips to an assistant bubble.
     *
     * @param aiMessage the assistant bubble to extend (ignored if {@code null})
     * @param replies   the chip labels to show
     */
    private void withQuickReplies(AiMessage aiMessage, String... replies) {
        if (aiMessage == null || replies.length == 0) {
            return;
        }
        aiMessage.addQuickReplies(List.of(replies), this::handleUserMessage);
    }

    /**
     * Handles a user-submitted message: appends the user bubble, shows the
     * typing indicator, and subscribes to the streaming assistant reply.
     * On stream error it falls back to a one-shot {@code sendMessage} call.
     *
     * @param message the user's message text
     */
    private void handleUserMessage(String message) {
        if (chatInput.isWaitingForResponse()) {
            return;
        }
        if (!customerService.exists(sessionId)) {
            redirectToSignIn();
            return;
        }

        addUserMessage(message);
        showTypingIndicator();

        String vaadinSessionId = sessionId;
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.push();
        }

        StringBuilder buffer = new StringBuilder();
        orderingService.streamMessage(vaadinSessionId, message).subscribe(
                chunk -> appendChunk(vaadinSessionId, buffer, chunk),
                error -> handleStreamError(ui, vaadinSessionId, message, buffer.toString(), error),
                () -> finishReply(ui, buffer.toString(), NO_RESPONSE_FALLBACK));
    }

    private void appendChunk(String sessionId, StringBuilder buffer, String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return;
        }
        buffer.append(chunk);
        log.debug("Stream chunk vaadin={} token='{}' bufLen={}",
                sessionId, chunk.replace("\n", "\\n"), buffer.length());
    }

    /**
     * Recovers from a failed stream: if nothing usable was received, asks the
     * service for a one-shot reply, then renders whatever we end up with.
     */
    private void handleStreamError(UI ui, String sessionId, String message, String rawText, Throwable error) {
        log.error("Stream failed vaadin={}: {}", sessionId, error.getMessage(), error);
        CompletableFuture.supplyAsync(() -> fallbackReply(sessionId, message, rawText), ioExecutor)
                .thenAccept(fallback -> finishReply(ui, rawText, fallback));
    }

    /**
     * Fetches a one-shot reply to stand in for a failed stream, or {@code null}
     * when the text already received is usable as-is.
     */
    private String fallbackReply(String sessionId, String message, String rawText) {
        if (!AssistantMessageSanitizer.isLeakedToolJson(rawText) && !rawText.isBlank()) {
            return null;
        }
        try {
            return orderingService.sendMessage(sessionId, message).message();
        } catch (Exception ex) {
            log.warn("Fallback sendMessage failed", ex);
            return SERVICE_DOWN_FALLBACK;
        }
    }

    /**
     * Renders the final assistant message, suppressing any leaked tool JSON and
     * clearing the waiting state.
     *
     * @param ui           the UI to update, or {@code null} if the view is gone
     * @param rawText      the text accumulated from the stream
     * @param fallbackText the text to use when {@code rawText} yields nothing usable
     */
    private void finishReply(UI ui, String rawText, String fallbackText) {
        if (ui == null) {
            return;
        }
        ui.access(() -> {
            hideTypingIndicator();
            boolean leaked = AssistantMessageSanitizer.isLeakedToolJson(rawText);
            if (leaked) {
                log.warn("Leaked tool JSON in stream, suppressing");
            }
            String clean = leaked ? "" : rawText;
            AiMessage ai = addAssistantMessage(clean.isBlank() ? fallbackText : clean);
            withQuickReplies(ai, STREAM_QUICK_REPLIES);
            chatInput.setWaitingForResponse(false);
            scrollToBottom();
            ui.push();
        });
    }

    private void showTypingIndicator() {
        chatInput.setWaitingForResponse(true);
        typingIndicator.setVisible(true);
        if (typingIndicator.getParent().isEmpty()) {
            chatContainer.add(typingIndicator);
        }
        scrollToBottom();
    }

    private void hideTypingIndicator() {
        typingIndicator.setVisible(false);
        if (typingIndicator.getParent().isPresent()) {
            chatContainer.remove(typingIndicator);
        }
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
     * before rendering, then decorating it with any link shortcuts.
     *
     * @param message the assistant's raw reply text
     * @return the rendered bubble, or {@code null} if the sanitized text was blank
     */
    private AiMessage addAssistantMessage(String message) {
        String clean = AssistantMessageSanitizer.sanitize(message);
        if (clean.isBlank()) {
            return null;
        }
        AiMessage ai = new AiMessage(clean);
        chatContainer.add(ai);
        attachLinkChips(ai, clean);
        return ai;
    }

    /**
     * Attaches quick-reply chips for any links mentioned in the assistant
     * reply. Tapping a chip sends the link's URL back to the AI as a message.
     *
     * @param ai   the bubble to decorate
     * @param text the sanitized reply text to scan for links
     */
    private void attachLinkChips(AiMessage ai, String text) {
        if (ai == null) {
            return;
        }
        ai.addQuickRepliesWithPayloads(MenuChipFactory.linkReplies(text), this::handleUserMessage);
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
