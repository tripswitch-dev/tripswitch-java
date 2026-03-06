package dev.tripswitch;

import java.util.Map;

/**
 * Router identity and metadata from the metadata cache.
 *
 * @param id       the router ID
 * @param name     the router name
 * @param metadata user-defined key-value metadata
 */
public record RouterMeta(String id, String name, Map<String, String> metadata) {}
