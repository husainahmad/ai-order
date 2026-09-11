package com.harmoni.pos.aiorder.client;

import com.harmoni.pos.aiorder.dto.CreateCustomerRequest;
import com.harmoni.pos.aiorder.dto.CustomerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * HTTP client for the customer backend customer CRUD endpoints.
 * <p>
 * Wraps {@code /api/v1/customers}: create, search-by-phone, and lookup-by-id.
 * All calls are blocking and configured with the customer backend base URL.
 *
 * @author Husain Harmoni
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerClient {

    @Qualifier("customerWebClient")
    private final WebClient webClient;

    /**
     * Creates a new customer in the customer backend.
     *
     * @param request the customer details (name, phone, email)
     * @return the created customer response
     */
    public CustomerResponse create(CreateCustomerRequest request) {
        log.debug("POST /api/v1/customers name={}", request.name());
        return webClient.post()
                .uri("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(CustomerResponse.class)
                .block();
    }

    /**
     * Searches for customers matching the exact phone number.
     *
     * @param phone the phone number to look up
     * @return matching customers, or an empty list if none found
     */
    public List<CustomerResponse> searchByPhone(String phone) {
        String uri = UriComponentsBuilder.fromPath("/api/v1/customers")
                .queryParam("phone", phone)
                .queryParam("size", 10)
                .toUriString();
        log.debug("GET /api/v1/customers?phone={}", phone);
        Map<String, Object> page = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        if (page == null || !page.containsKey("content")) return List.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        return content.stream()
                .map(m -> new CustomerResponse(
                        ((Number) m.get("id")).longValue(),
                        (String) m.get("name"),
                        (String) m.get("phone"),
                        (String) m.get("email")))
                .toList();
    }

    /**
     * Fetches a single customer by its numeric id.
     *
     * @param id the customer id
     * @return the customer response
     */
    public CustomerResponse getById(Long id) {
        log.debug("GET /api/v1/customers/{}", id);
        return webClient.get()
                .uri("/api/v1/customers/{id}", id)
                .retrieve()
                .bodyToMono(CustomerResponse.class)
                .block();
    }

    /**
     * Thrown when the customer backend returns a non-success HTTP response.
     */
    public static class CustomerApiException extends RuntimeException {

        /** Constructs the exception with a message. */
        public CustomerApiException(String message) { super(message); }

        /** Constructs the exception with a message and underlying cause. */
        public CustomerApiException(String message, Throwable cause) { super(message, cause); }
    }
}