package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A router configuration. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Router(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("mode") RouterMode mode,
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("breaker_count") int breakerCount,
        @JsonProperty("breakers") List<Breaker> breakers,
        @JsonProperty("inserted_at") Instant insertedAt,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("metadata") Map<String, String> metadata
) {}
