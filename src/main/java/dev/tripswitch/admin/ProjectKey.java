package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** A project API key (eb_pk_...). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectKey(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("key_prefix") String keyPrefix,
        @JsonProperty("last_used_at") Instant lastUsedAt,
        @JsonProperty("inserted_at") Instant insertedAt
) {}
