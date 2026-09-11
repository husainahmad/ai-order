package com.harmoni.pos.aiorder.dto;

/**
 * A customer session opened against the customer backend.
 *
 * @param id            the session id
 * @param customerId    the owning customer id
 * @param sessionToken  opaque token identifying the session
 * @param source        where the session came from, e.g. {@code AI_CHAT}
 * @param status        current session status
 */
public record CustomerSessionResponse(Long id, Long customerId, String sessionToken, String source, String status) {}