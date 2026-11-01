package com.harmoni.pos.aiorder.dto;

import java.util.List;

/**
 * A single selectable option within a customization group.
 *
 * @param id          the customization option id
 * @param name        the option display name
 * @param price       the additional price as a formatted string, possibly empty
 * @param isDefault   whether this option is selected by default
 * @param isRequired  whether the option is required
 * @param maxQuantity maximum quantity allowed for this option
 */
public record CustomizationOption(
        int id,
        String name,
        String price,
        Boolean isDefault,
        Boolean isRequired,
        Integer maxQuantity
) {
}
