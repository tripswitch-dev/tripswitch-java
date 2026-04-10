package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** A Tripswitch workspace. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Workspace(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("slug") String slug,
        @JsonProperty("org_id") String orgId,
        @JsonProperty("inserted_at") Instant insertedAt
) {}
