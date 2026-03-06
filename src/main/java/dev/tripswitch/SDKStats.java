package dev.tripswitch;

import java.time.Instant;

/**
 * Health metrics snapshot for the SDK.
 *
 * @param droppedSamples      number of samples dropped due to full buffer
 * @param bufferSize          current sample buffer size
 * @param sseConnected        whether SSE connection is active
 * @param sseReconnects       number of SSE reconnection attempts
 * @param lastSuccessfulFlush time of last successful flush
 * @param lastSseEvent        time of last SSE event received
 * @param flushFailures       number of batches dropped after retry exhaustion
 * @param cachedBreakers      number of breakers in local state cache
 */
public record SDKStats(
        long droppedSamples,
        int bufferSize,
        boolean sseConnected,
        long sseReconnects,
        Instant lastSuccessfulFlush,
        Instant lastSseEvent,
        long flushFailures,
        int cachedBreakers
) {}
