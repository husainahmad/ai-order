package com.harmoni.pos.aiorder.dto;

import java.util.List;

/**
 * Unified response payload for the ordering service.
 * <p>
 * Carries the assistant's reply text together with any recommended
 * products or categories attached to the reply.
 *
 * @param message    the assistant's reply text
 * @param products   recommended products, possibly empty
 * @param categories recommended categories, possibly empty
 */
public record ChatResponse(String message, List<ProductRecommendation> products, List<CategoryRecommendation> categories) {}