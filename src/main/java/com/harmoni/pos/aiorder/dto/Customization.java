package com.harmoni.pos.aiorder.dto;

import java.util.List;

/**
 * A customization group for a menu product (e.g., Sugar Level, Ice Level, Toppings).
 *
 * @param id           the customization group id
 * @param name         the group display name
 * @param type         the input type (e.g., radio, checkbox, select)
 * @param required     whether a selection is required
 * @param minQuantity  minimum number of options to select
 * @param maxQuantity  maximum number of options to select
 * @param options      available options in this group
 */
public record Customization(
        int id,
        String name,
        String type,
        Boolean required,
        Integer minQuantity,
        Integer maxQuantity,
        List<CustomizationOption> options
) {
}
