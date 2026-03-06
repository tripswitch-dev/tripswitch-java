package dev.tripswitch;

import java.util.List;

/**
 * Response from metadata list operations, including ETag for conditional requests.
 *
 * @param items   the metadata items
 * @param newEtag the ETag value for conditional requests
 * @param <T>     the type of metadata (BreakerMeta or RouterMeta)
 */
public record MetadataResponse<T>(List<T> items, String newEtag) {}
