package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Input for linking a breaker to a router. */
public record LinkBreakerInput(
        @JsonProperty("breaker_id") String breakerId
) {}
