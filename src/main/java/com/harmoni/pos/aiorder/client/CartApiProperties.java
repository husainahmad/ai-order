package com.harmoni.pos.aiorder.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "customer.api.cart")
public class CartApiProperties {
    private String url = "http://localhost:8084";
    private CartEndpoints endpoints = new CartEndpoints();
    private int timeoutMs = 60000;

    @Data
    public static class CartEndpoints {
        private String basePath = "/api/v1/customer-sessions";
        private String addItem = "/cart/items";
    }
}
