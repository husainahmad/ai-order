package com.harmoni.pos.aiorder.dto;

/**
 * A registered customer returned by the customer backend.
 *
 * @param id    the customer id
 * @param name  customer display name
 * @param phone phone number, may be {@code null}
 * @param email email address, may be {@code null}
 */
public record CustomerResponse(Long id, String name, String phone, String email) {}