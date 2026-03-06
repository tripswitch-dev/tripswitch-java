package dev.tripswitch;

import java.util.Map;

/**
 * Breaker identity and metadata from the metadata cache.
 *
 * @param id       the breaker ID
 * @param name     the breaker name
 * @param metadata user-defined key-value metadata
 */
public record BreakerMeta(String id, String name, Map<String, String> metadata) {}
