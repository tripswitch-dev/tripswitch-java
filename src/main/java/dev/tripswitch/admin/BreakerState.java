package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Current state of a circuit breaker. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BreakerState(
        @JsonProperty("breaker_id") String breakerId,
        @JsonProperty("state") String state,
        @JsonProperty("allow_rate") double allowRate,
        @JsonProperty("updated_at") Instant updatedAt
) {}
