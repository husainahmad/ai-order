package com.harmoni.pos.aiorder.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the customer backend HTTP API, bound from the
 * {@code customer.api.*} application properties.
 *
 * @param baseUrl      the customer backend base URL, e.g. {@code http://localhost:8084}
 * @param timeoutMs    connect/response timeout in milliseconds
 * @param source       the source label sent when opening customer sessions
 * @author Husain Harmoni
 */
@ConfigurationProperties(prefix = "customer.api")
public record CustomerApiProperties(
        String baseUrl,
        @DefaultValue("60000") int timeoutMs,
        @DefaultValue("AI_CHAT") String source
) {}