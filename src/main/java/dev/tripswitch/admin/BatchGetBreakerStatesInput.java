package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Input for batch-retrieving breaker states. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchGetBreakerStatesInput(
        @JsonProperty("breaker_ids") List<String> breakerIds,
        @JsonProperty("router_id") String routerId
) {}
