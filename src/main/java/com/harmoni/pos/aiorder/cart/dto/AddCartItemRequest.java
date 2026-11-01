package com.harmoni.pos.aiorder.cart.dto;

import java.util.ArrayList;
import java.util.List;

public class AddCartItemRequest {
    private Long productId;
    private Long skuId;
    private int quantity;
    private List<CartCustomizationGroup> customizations = new ArrayList<>();

    public AddCartItemRequest() {}

    public AddCartItemRequest(Long productId, Long skuId, int quantity) {
        this.productId = productId;
        this.skuId = skuId;
        this.quantity = quantity;
    }

    public AddCartItemRequest(Long productId, Long skuId, int quantity, List<CartCustomizationGroup> customizations) {
        this.productId = productId;
        this.skuId = skuId;
        this.quantity = quantity;
        this.customizations = customizations != null ? customizations : new ArrayList<>();
    }

    public static class CartCustomizationGroup {
        private Long customizationId;
        private List<CartCustomizationOption> options;

        public CartCustomizationGroup(Long customizationId, List<CartCustomizationOption> options) {
            this.customizationId = customizationId;
            this.options = options;
        }

        public Long getCustomizationId() { return customizationId; }
        public List<CartCustomizationOption> getOptions() { return options; }
    }

    public static class CartCustomizationOption {
        private Long customizationOptionId;
        private int quantity;

        public CartCustomizationOption(Long customizationOptionId, int quantity) {
            this.customizationOptionId = customizationOptionId;
            this.quantity = quantity;
        }

        public CartCustomizationOption(Long customizationOptionId) {
            this(customizationOptionId, 1);
        }

        public Long getCustomizationOptionId() { return customizationOptionId; }
        public int getQuantity() { return quantity; }
    }

    public Long getProductId() { return productId; }
    public Long getSkuId() { return skuId; }
    public int getQuantity() { return quantity; }
    public List<CartCustomizationGroup> getCustomizations() { return customizations; }
}
