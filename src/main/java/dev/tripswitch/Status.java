package dev.tripswitch;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Project health status returned by the API.
 *
 * @param openCount   number of open breakers
 * @param closedCount number of closed breakers
 * @param lastEvalMs  timestamp of last evaluation in epoch milliseconds
 */
public record Status(
        @JsonProperty("open_count") int openCount,
        @JsonProperty("closed_count") int closedCount,
        @JsonProperty("last_eval_ms") long lastEvalMs
) {}
