package com.harmoni.pos.aiorder.dto;

import java.util.List;

/**
 * A menu product recommended by the AI assistant.
 *
 * @param id             the product id
 * @param name           the product display name
 * @param price          the product price as a formatted string
 * @param categoryId     the owning menu category id
 * @param customizations list of customization groups for this product
 */
public record ProductRecommendation(
        int id,
        String name,
        String price,
        int categoryId,
        List<Customization> customizations
) {
    public ProductRecommendation(int id, String name, String price, int categoryId) {
        this(id, name, price, categoryId, List.of());
    }
}
