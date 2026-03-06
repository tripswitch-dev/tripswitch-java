package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Input for bulk-syncing breakers. */
public record SyncBreakersInput(
        @JsonProperty("breakers") List<CreateBreakerInput> breakers
) {}
