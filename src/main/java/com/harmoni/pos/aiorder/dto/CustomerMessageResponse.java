package com.harmoni.pos.aiorder.dto;

/**
 * A single chat message exchanged with the AI assistant.
 *
 * @param id        the message id
 * @param sessionId the customer session the message belongs to
 * @param role      sender role, e.g. {@code USER} or {@code ASSISTANT}
 * @param message   the message text
 */
public record CustomerMessageResponse(Long id, Long sessionId, String role, String message) {}