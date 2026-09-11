package com.harmoni.pos.aiorder.client;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.harmoni.pos.aiorder.dto.ProductRecommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

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
            Map<String, Object> root = objectMapper.readValue(raw, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) root.getOrDefault("data", List.of());
            return items.stream()
                    .map(m -> new ProductRecommendation(
                            ((Number) m.get("id")).intValue(),
                            (String) m.get("name"),
                            m.get("price") != null ? m.get("price").toString() : "",
                            m.get("categoryId") != null ? ((Number) m.get("categoryId")).intValue() : 0))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to parse products: {}", e.getMessage());
            return List.of();
        }
    }
}