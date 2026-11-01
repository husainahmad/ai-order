package com.harmoni.pos.aiorder.client;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.harmoni.pos.aiorder.dto.Customization;
import com.harmoni.pos.aiorder.dto.CustomizationOption;
import com.harmoni.pos.aiorder.dto.ProductRecommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the customer backend menu-product endpoints.
 * <p>
 * Fetches products for a category with real prices, proxied through the
 * customer service so prices stay consistent with the menu service.
 *
 * @author Husain Harmoni
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductClient {

    @Qualifier("customerWebClient")
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final CustomerApiProperties properties;

    /**
     * Lists the products for a given category id.
     *
     * @param sessionId  the customer session id
     * @param categoryId the menu category id
     * @return matching products with prices, or an empty list if none found
     */
    public List<ProductRecommendation> getByCategory(Long sessionId, int categoryId) {
        log.debug("GET {}/products/category/{} sessionId={}", properties.sessionsPath(), categoryId, sessionId);
        String raw = webClient.get()
                .uri(properties.sessionsPath() + "/{sessionId}/products/category/{categoryId}", sessionId, categoryId)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parseProducts(raw);
    }

    /**
     * Parses the raw JSON body into product recommendation records.
     * The backend may wrap the array under a {@code data} key.
     *
     * @param raw the raw JSON response body
     * @return parsed products, or an empty list on any parse failure
     */
    private List<ProductRecommendation> parseProducts(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            List<Map<String, Object>> items = new ArrayList<>();
            if (parsed instanceof Map<?, ?> rootMap) {
                Object data = rootMap.get("data");
                if (data instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m) {
                            items.add((Map<String, Object>) m);
                        }
                    }
                } else if (data instanceof Map<?, ?>) {
                    // single object wrapped
                    items.add((Map<String, Object>) data);
                }
            } else if (parsed instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        items.add((Map<String, Object>) m);
                    }
                }
            }

            return items.stream()
                    .map(m -> new ProductRecommendation(
                            m.get("id") instanceof Number n ? n.intValue() : 0,
                            m.get("name") instanceof String s ? s : "",
                            m.get("price") != null ? m.get("price").toString() : "",
                            m.get("categoryId") instanceof Number cn ? cn.intValue() : (m.get("category_id") instanceof Number cn2 ? cn2.intValue() : 0),
                            parseCustomizations(m.get("customizations"))
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to parse products: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Customization> parseCustomizations(Object customizationsObj) {
        if (!(customizationsObj instanceof List<?> list)) {
            return List.of();
        }
        List<Customization> result = new ArrayList<>();
        for (Object cObj : list) {
            if (!(cObj instanceof Map<?, ?> cMap)) continue;
            List<CustomizationOption> options = parseCustomizationOptions(cMap.get("options"));
            result.add(new Customization(
                    cMap.get("id") instanceof Number n ? n.intValue() : 0,
                    cMap.get("name") instanceof String s ? s : "",
                    cMap.get("type") instanceof String t ? t : (cMap.get("inputType") instanceof String t2 ? t2 : ""),
                    getBoolean(cMap.get("required"), cMap.get("isRequired")),
                    getInteger(cMap.get("minQuantity"), cMap.get("min")),
                    getInteger(cMap.get("maxQuantity"), cMap.get("max")),
                    options
            ));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<CustomizationOption> parseCustomizationOptions(Object optionsObj) {
        if (!(optionsObj instanceof List<?> list)) {
            return List.of();
        }
        List<CustomizationOption> result = new ArrayList<>();
        for (Object oObj : list) {
            if (!(oObj instanceof Map<?, ?> oMap)) continue;
            result.add(new CustomizationOption(
                    oMap.get("id") instanceof Number n ? n.intValue() : 0,
                    oMap.get("name") instanceof String s ? s : "",
                    oMap.get("price") != null ? oMap.get("price").toString() : "",
                    getBoolean(oMap.get("isDefault"), oMap.get("defaultValue")),
                    getBoolean(oMap.get("isRequired"), oMap.get("required")),
                    getInteger(oMap.get("maxQuantity"), oMap.get("max"))
            ));
        }
        return result;
    }

    private Boolean getBoolean(Object... values) {
        for (Object v : values) {
            if (v instanceof Boolean b) return b;
            if (v instanceof String s) {
                String lower = s.toLowerCase();
                if (lower.equals("true") || lower.equals("1") || lower.equals("yes")) return true;
                if (lower.equals("false") || lower.equals("0") || lower.equals("no")) return false;
            }
            if (v instanceof Number n) return n.intValue() != 0;
        }
        return null;
    }

    private Integer getInteger(Object... values) {
        for (Object v : values) {
            if (v instanceof Number n) return n.intValue();
            if (v instanceof String s) {
                try {
                    return Integer.parseInt(s.trim());
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
