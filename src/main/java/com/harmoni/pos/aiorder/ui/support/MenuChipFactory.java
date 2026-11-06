package com.harmoni.pos.aiorder.ui.support;

import com.harmoni.pos.aiorder.dto.CategoryRecommendation;
import com.harmoni.pos.aiorder.dto.Customization;
import com.harmoni.pos.aiorder.dto.CustomizationOption;
import com.harmoni.pos.aiorder.dto.ProductRecommendation;
import com.harmoni.pos.aiorder.ui.component.QuickReplyChips;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the suggestion chips shown below an assistant message.
 * <p>
 * A chip pairs a short label with the message text it sends. The label is what the
 * customer reads; the payload is what the assistant receives, which is often a full order
 * intent rather than the label itself. Keeping that translation here leaves the view to do
 * nothing but attach the resulting chips.
 *
 * @author Husain Harmoni
 */
public final class MenuChipFactory {

    private static final int MAX_CATEGORY_CHIPS = 6;
    private static final int MAX_PRODUCT_CHIPS = 8;
    private static final int MAX_PRODUCT_OPTION_SUMMARY = 3;
    private static final int MAX_CUSTOMIZATION_OPTION_SUMMARY = 4;
    private static final int MAX_PAYLOAD_OPTIONS = 3;
    private static final int MAX_PAYLOAD_LENGTH = 120;
    private static final int MAX_LINK_CHIPS = 4;

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)");
    private static final Pattern BARE_URL = Pattern.compile("https?://[^\\s)>\"]+");

    private MenuChipFactory() {
    }

    /**
     * Builds chips for recommended products. The label shows the name and price; tapping it
     * drafts an order intent, including the product's customizations when it has any.
     *
     * @param products the products to render, not {@code null}
     * @return the chips to attach
     */
    public static List<QuickReplyChips.Reply> productReplies(List<ProductRecommendation> products) {
        return products.stream()
                .limit(MAX_PRODUCT_CHIPS)
                .map(product -> new QuickReplyChips.Reply(
                        product.name() + formatPrice(product.price()),
                        productIntent(product)))
                .toList();
    }

    /**
     * Builds chips for menu categories. The label and the draft are the category name.
     *
     * @param categories the categories to render, not {@code null}
     * @return the chips to attach
     */
    public static List<QuickReplyChips.Reply> categoryReplies(List<CategoryRecommendation> categories) {
        return categories.stream()
                .limit(MAX_CATEGORY_CHIPS)
                .map(category -> new QuickReplyChips.Reply(category.name(), category.name()))
                .toList();
    }

    /**
     * Builds chips for customization groups. The label lists the group and its options; the
     * draft asks the assistant to apply a choice.
     *
     * @param customizations the customization groups to render, not {@code null}
     * @return the chips to attach
     */
    public static List<QuickReplyChips.Reply> customizationReplies(List<Customization> customizations) {
        return customizations.stream()
                .filter(c -> c != null && c.name() != null && !c.name().isBlank())
                .limit(MAX_CATEGORY_CHIPS)
                .map(MenuChipFactory::customizationReply)
                .toList();
    }

    /**
     * Builds chips for any links mentioned in the reply: markdown links ({@code [text](url)})
     * and bare {@code https://…} URLs. Tapping a chip sends the link's URL back to the AI.
     *
     * @param text the reply text to scan, may be {@code null}
     * @return the chips to attach, empty when no links are present
     */
    public static List<QuickReplyChips.Reply> linkReplies(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        LinkedHashMap<String, String> labelByUrl = new LinkedHashMap<>();
        Matcher markdown = MARKDOWN_LINK.matcher(text);
        while (markdown.find()) {
            String label = markdown.group(1).trim();
            String url = markdown.group(2).trim();
            if (!label.isEmpty() && !url.isEmpty()) {
                labelByUrl.putIfAbsent(url, label);
            }
        }
        Matcher bare = BARE_URL.matcher(text);
        while (bare.find()) {
            String url = bare.group().trim().replaceAll("[.,;:)]+$", "");
            labelByUrl.putIfAbsent(url, safeHost(url));
        }
        return labelByUrl.entrySet().stream()
                .limit(MAX_LINK_CHIPS)
                .map(entry -> new QuickReplyChips.Reply(
                        entry.getValue().isBlank() ? safeHost(entry.getKey()) : entry.getValue(),
                        entry.getKey()))
                .toList();
    }

    // ------------------------------------------------------------------ internals

    private static String productIntent(ProductRecommendation product) {
        StringBuilder draft = new StringBuilder("Saya mau pesan ").append(product.name());
        if (product.customizations() == null) {
            return draft.toString();
        }
        product.customizations().stream()
                .filter(c -> c != null && c.name() != null && !c.name().isBlank())
                .forEach(c -> {
                    draft.append(" (").append(c.name());
                    String options = summarizeOptions(c.options(), MAX_PRODUCT_OPTION_SUMMARY, true);
                    if (!options.isEmpty()) {
                        draft.append(": ").append(options);
                    }
                    if (c.options() != null && c.options().size() > MAX_PRODUCT_OPTION_SUMMARY) {
                        draft.append(", ...");
                    }
                    draft.append(")");
                });
        return draft.toString();
    }

    private static QuickReplyChips.Reply customizationReply(Customization customization) {
        String options = summarizeOptions(customization.options(), MAX_CUSTOMIZATION_OPTION_SUMMARY, true);
        String label;
        if (customization.options() != null && !customization.options().isEmpty()) {
            StringBuilder sb = new StringBuilder(customization.name()).append(": ").append(options);
            if (customization.options().size() > MAX_CUSTOMIZATION_OPTION_SUMMARY) {
                sb.append(", ...");
            }
            label = sb.toString();
        } else {
            label = customization.name();
        }
        return new QuickReplyChips.Reply(label, customizationPayload(customization));
    }

    private static String customizationPayload(Customization customization) {
        String payload = "Pilih " + customization.name();
        if (customization.options() != null && !customization.options().isEmpty()) {
            payload += ": " + summarizeOptions(customization.options(), MAX_PAYLOAD_OPTIONS, false);
        }
        return payload.length() > MAX_PAYLOAD_LENGTH ? "Pilih " + customization.name() : payload;
    }

    /**
     * Joins an option list into a single summary string.
     *
     * @param options      the options, possibly {@code null}
     * @param limit        the maximum number of options to include
     * @param includePrice whether to append each option's surcharge
     * @return the comma-joined summary, or an empty string when there is nothing to show
     */
    private static String summarizeOptions(List<CustomizationOption> options, int limit, boolean includePrice) {
        if (options == null) {
            return "";
        }
        return options.stream()
                .filter(o -> o != null && o.name() != null)
                .limit(limit)
                .map(o -> includePrice ? o.name() + formatOptionPrice(o.price()) : o.name())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static String formatOptionPrice(String price) {
        return price != null && !price.isBlank() ? " +" + price : "";
    }

    private static String formatPrice(String price) {
        return price != null && !price.isBlank() ? " · " + price : "";
    }

    /**
     * Extracts a readable host name from a URL for bare-link chip labels.
     *
     * @param url the URL
     * @return the host (minus the {@code www.} prefix), or the URL on failure
     */
    private static String safeHost(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                return url;
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception ex) {
            return url;
        }
    }
}
