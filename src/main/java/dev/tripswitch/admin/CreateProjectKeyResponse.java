package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response from creating a project key. The key is only returned on creation. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateProjectKeyResponse(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("key") String key,
        @JsonProperty("key_prefix") String keyPrefix,
        @JsonProperty("message") String message
) {}
