package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** A breaker state transition event. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Event(
        @JsonProperty("id") String id,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("breaker_id") String breakerId,
        @JsonProperty("from_state") String fromState,
        @JsonProperty("to_state") String toState,
        @JsonProperty("reason") String reason,
        @JsonProperty("timestamp") Instant timestamp
) {}
