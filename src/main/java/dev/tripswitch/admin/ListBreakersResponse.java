package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Response from listing breakers. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ListBreakersResponse(
        @JsonProperty("breakers") List<Breaker> breakers,
        @JsonProperty("count") int count,
        @JsonProperty("hash") String hash,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("next_cursor") String nextCursor
) {}
