package com.harmoni.pos.aiorder.client;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.harmoni.pos.aiorder.dto.CategoryRecommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * HTTP client for the customer backend menu-category endpoints.
 * <p>
 * Fetches categories for the default brand, proxied through the customer
 * service so the Vaadin UI never talks to the menu service directly.
 *
 * @author Husain Harmoni
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryClient {

    @Qualifier("customerWebClient")
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * Retrieves the menu categories for the given customer session.
     *
     * @param sessionId the customer session id
     * @return list of category recommendations; empty if the backend returns nothing parseable
     */
    public List<CategoryRecommendation> getCategories(Long sessionId) {
        log.debug("GET /api/v1/customer-sessions/{}/categories", sessionId);
        String raw = webClient.get()
                .uri("/api/v1/customer-sessions/{sessionId}/categories", sessionId)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parseCategories(raw);
    }

    /**
     * Parses the raw JSON array returned by the backend into recommendation records.
     *
     * @param raw the raw JSON response body
     * @return parsed categories, or an empty list on any parse failure
     */
    private List<CategoryRecommendation> parseCategories(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            List<Map<String, Object>> items = objectMapper.readValue(raw, new TypeReference<>() {});
            return items.stream()
                    .map(m -> new CategoryRecommendation(
                            ((Number) m.get("id")).intValue(),
                            (String) m.get("name")))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to parse categories: {}", e.getMessage());
            return List.of();
        }
    }
}