package com.harmoni.pos.aiorder.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for registering a new customer.
 *
 * @param name  customer display name, required
 * @param phone phone number, optional but must match a phone format when present
 * @param email email address, optional but must be well-formed when present
 */
public record CreateCustomerRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 30) @Pattern(regexp = "^[0-9+\\-()\\s]{5,30}$", message = "Invalid phone format") String phone,
        @Size(max = 150) @Email String email
) {}