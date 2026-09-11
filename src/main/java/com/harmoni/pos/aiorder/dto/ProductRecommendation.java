package com.harmoni.pos.aiorder.dto;

/**
 * A menu product recommended by the AI assistant.
 *
 * @param id         the product id
 * @param name       the product display name
 * @param price      the product price as a formatted string
 * @param categoryId the owning menu category id
 */
public record ProductRecommendation(int id, String name, String price, int categoryId) {}