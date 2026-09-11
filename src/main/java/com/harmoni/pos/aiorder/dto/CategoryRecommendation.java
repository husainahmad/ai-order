package com.harmoni.pos.aiorder.dto;

/**
 * A menu category recommended by the AI assistant.
 *
 * @param id   the category id
 * @param name the category display name
 */
public record CategoryRecommendation(int id, String name) {}