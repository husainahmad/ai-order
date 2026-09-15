package com.harmoni.pos.aiorder.ui;

import com.harmoni.pos.aiorder.client.CategoryClient;
import com.harmoni.pos.aiorder.client.ProductClient;
import com.harmoni.pos.aiorder.dto.CategoryRecommendation;
import com.harmoni.pos.aiorder.dto.CustomerResponse;
import com.harmoni.pos.aiorder.dto.ProductRecommendation;
import com.harmoni.pos.aiorder.service.CustomerService;
import com.harmoni.pos.aiorder.service.OrderingService;
import com.harmoni.pos.aiorder.ui.component.*;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
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
    private static final Pattern CATEGORY_PRODUCT_ID = Pattern.compile("(?i)\\b(?:kategori|produk|category|product|menu)\\s*(?:[#:]\\s*)?IDs?\\s*[#:]?\\s*\\d+\\b");
    private static final Pattern STANDALONE_ID = Pattern.compile("(?i)\\bIDs?\\s*[#:]?\\s*\\d+\\b");
    private static final Pattern ID_CELL = Pattern.compile("(?i)^\\s*ID\\s*$");
    private static final Pattern SEPARATOR_CELL = Pattern.compile("^:?-{2,}:?$|^-{2,}$|^\\s*$");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern BARE_URL = Pattern.compile("https?://[^\\s)>\"]+");

    private final OrderingService orderingService;
    private final CustomerService customerService;
    private final Environment environment;
    private final Validator validator;
    private final CategoryClient categoryClient;
    private final ProductClient productClient;

    private static final int MAX_CATEGORY_CHIPS = 6;
    private static final int MAX_PRODUCT_CHIPS = 8;

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
     * shows the customer gate or resumes the conversation for the linked customer.
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
        CustomerGateDialog dialog = new CustomerGateDialog(sessionId, environment.getProperty("app.store-name", "Kopi Harmoni"), customerService, validator, this::onCustomerRegistered);
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
        UI ui = UI.getCurrent();
        CompletableFuture.supplyAsync(() -> {
            try {
                Long customerSessionId = orderingService.ensureSession(sessionId);
                if (customerSessionId == null) {
                    return List.<CategoryRecommendation>of();
                }
                return categoryClient.getCategories(customerSessionId);
            } catch (Exception ex) {
                log.warn("Failed to load menu categories: {}", ex.getMessage());
                return List.<CategoryRecommendation>of();
            }
        }, ioExecutor).thenAccept(categories -> {
            if (ui == null) return;
            ui.access(() -> {
                if (categories == null || categories.isEmpty()) {
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

        UI ui = UI.getCurrent();
        if (ui == null) return;
        CompletableFuture.supplyAsync(() -> {
            try {
                Long customerSessionId = orderingService.ensureSession(sessionId);
                if (customerSessionId == null || categoryId <= 0) {
                    return List.<ProductRecommendation>of();
                }
                return productClient.getByCategory(customerSessionId, categoryId);
            } catch (Exception ex) {
                log.warn("Failed to load products for category '{}': {}", categoryName, ex.getMessage());
                return List.<ProductRecommendation>of();
            }
        }, ioExecutor).thenAccept(products -> ui.access(() -> {
            if (products == null || products.isEmpty()) {
                return;
            }
            AiMessage ai = addAssistantMessage("Menu **" + categoryName + "** — ketuk produk untuk mengisi pesan:");
            if (ai != null) {
                attachProductChips(ai, products);
            }
            scrollToBottom();
        }));
    }

    /**
     * Adds product chips to an assistant bubble: the chip label shows the
     * product name (and price), while tapping it fills the composer with an
     * order intent.
     *
     * @param ai       the bubble to decorate
     * @param products the products to render as chips
     */
    private void attachProductChips(AiMessage ai, List<ProductRecommendation> products) {
        List<QuickReplyChips.Reply> replies = products.stream()
                .limit(MAX_PRODUCT_CHIPS)
                .map(p -> new QuickReplyChips.Reply(p.name() + formatPrice(p.price()), "Saya mau pesan " + p.name()))
                .toList();
        ai.addQuickRepliesWithPayloads(replies, this::fillInput);
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
     * Formats an optional product price for display.
     *
     * @param price the raw price string, possibly blank
     * @return a formatted suffix, or an empty string when no price is present
     */
    private String formatPrice(String price) {
        return (price != null && !price.isBlank()) ? " · " + price : "";
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
                                        log.warn("Fallback sendMessage failed", ex);
                                        fallbackText = "Maaf, AI sedang tidak tersedia.";
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
                                AiMessage ai = addAssistantMessage(finalText.isBlank() ? fallbackText : finalText);
                                withQuickReplies(ai, "Lihat menu lagi", "Rekap pesanan", "Selesai / bayar");
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
                            AiMessage ai = addAssistantMessage(finalText.isBlank() ? "Maaf, tidak ada respons AI." : finalText);
                            withQuickReplies(ai, "Lihat menu lagi", "Rekap pesanan", "Selesai / bayar");
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
     * @return the rendered bubble, or {@code null} if the sanitized text was blank
     */
    private AiMessage addAssistantMessage(String message) {
        if (message == null) return null;
        String clean = sanitizeAssistantText(message);
        if (clean.isBlank()) return null;
        AiMessage ai = new AiMessage(clean);
        chatContainer.add(ai);
        attachLinkChips(ai, clean);
        return ai;
    }

    /**
     * Attaches quick-reply chips for any links mentioned in the assistant
     * reply: markdown links ({@code [text](url)}) and bare {@code https://…}
     * URLs. Tapping a chip sends the link's URL back to the AI as a message.
     *
     * @param ai   the bubble to decorate
     * @param text the sanitized reply text to scan for links
     */
    private void attachLinkChips(AiMessage ai, String text) {
        if (ai == null || text == null || text.isBlank()) {
            return;
        }
        LinkedHashMap<String, String> labelByUrl = new LinkedHashMap<>();
        java.util.regex.Matcher md = MARKDOWN_LINK.matcher(text);
        while (md.find()) {
            String label = md.group(1).trim();
            String url = md.group(2).trim();
            if (!label.isEmpty() && !url.isEmpty()) {
                labelByUrl.putIfAbsent(url, label);
            }
        }
        java.util.regex.Matcher bare = BARE_URL.matcher(text);
        while (bare.find()) {
            String url = bare.group().trim().replaceAll("[.,;:)]+$", "");
            labelByUrl.putIfAbsent(url, safeHost(url));
        }
        if (labelByUrl.isEmpty()) {
            return;
        }
        List<QuickReplyChips.Reply> links = labelByUrl.entrySet().stream()
                .limit(4)
                .map(e -> new QuickReplyChips.Reply(
                        e.getValue().isBlank() ? safeHost(e.getKey()) : e.getValue(),
                        e.getKey()))
                .toList();
        ai.addQuickRepliesWithPayloads(links, this::handleUserMessage);
    }

    /**
     * Extracts a readable host name from a URL for bare-link chip labels.
     *
     * @param url the URL
     * @return the host (minus the {@code www.} prefix), or the URL on failure
     */
    private String safeHost(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                return url;
            }
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host;
        } catch (Exception ex) {
            return url;
        }
    }

    /**
     * Cleans the model's raw streamed text for display.
     * <p>
     * Removes Claude-style tool-call JSON ({@code {"name","arguments"}}), strips
     * control-token tool-call segments ({@code <|start|>...<|call|>}), keeps only
     * the text of a trailing {@code final} channel, drops any remnant of a
     * {@code thinking} block, and hides internal category/product IDs so raw
     * identifiers never reach the customer.
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
        clean = CATEGORY_PRODUCT_ID.matcher(clean).replaceAll("").trim();
        clean = STANDALONE_ID.matcher(clean).replaceAll("").trim();
        clean = stripIdColumn(clean);
        clean = clean.replaceAll(" {2,}", " ").trim();
        return clean.replaceAll("([,;:])(?=[^\\s])", "$1 ").trim();
    }

    /**
     * Removes an {@code ID} column from markdown pipe-tables in the reply.
     * <p>
     * The model sometimes echoes internal category/product tables including an
     * {@code ID} column; this drops that column while keeping the meaningful
     * columns (names, prices) intact.
     *
     * @param text the reply text
     * @return the text with any {@code ID} table column removed
     */
    private String stripIdColumn(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            if (!lines[i].trim().startsWith("|")) {
                result.append(lines[i]).append('\n');
                i++;
                continue;
            }
            List<String> block = new ArrayList<>();
            while (i < lines.length && lines[i].trim().startsWith("|")) {
                block.add(lines[i]);
                i++;
            }
            Integer idCol = null;
            for (String row : block) {
                List<String> cells = splitTableCells(row);
                if (cells.isEmpty() || isSeparatorRow(cells)) continue;
                for (int c = 0; c < cells.size(); c++) {
                    if (ID_CELL.matcher(cells.get(c)).matches()) {
                        idCol = c;
                        break;
                    }
                }
                if (idCol != null) break;
            }
            if (idCol == null) {
                for (String row : block) result.append(row).append('\n');
            } else {
                for (String row : block) {
                    List<String> cells = splitTableCells(row);
                    if (idCol < cells.size()) {
                        cells.remove(idCol);
                    }
                    result.append(rebuildTableRow(cells)).append('\n');
                }
            }
        }
        return result.toString().stripTrailing();
    }

    /**
     * Splits a markdown pipe-table row into its cell texts, dropping the
     * leading and trailing empty tokens produced by the outer pipes.
     *
     * @param row the raw table row
     * @return the trimmed cell texts
     */
    private List<String> splitTableCells(String row) {
        List<String> cells = new ArrayList<>();
        for (String part : row.split("\\|", -1)) {
            cells.add(part.trim());
        }
        if (!cells.isEmpty() && cells.get(0).isEmpty()) cells.remove(0);
        if (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) cells.remove(cells.size() - 1);
        return cells;
    }

    /**
     * Rebuilds a pipe-table row from cell texts.
     *
     * @param cells the cell texts
     * @return the row in markdown pipe-table form
     */
    private String rebuildTableRow(List<String> cells) {
        if (cells.isEmpty()) {
            return "| |";
        }
        return "| " + String.join(" | ", cells) + " |";
    }

    /**
     * Detects whether a table row is a GFM column-alignment separator row.
     *
     * @param cells the row's cell texts
     * @return {@code true} if every cell is an alignment marker or blank
     */
    private boolean isSeparatorRow(List<String> cells) {
        for (String cell : cells) {
            if (!SEPARATOR_CELL.matcher(cell).matches()) {
                return false;
            }
        }
        return true;
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