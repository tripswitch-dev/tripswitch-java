package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Response from listing project keys. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ListProjectKeysResponse(
        @JsonProperty("keys") List<ProjectKey> keys,
        @JsonProperty("count") int count
) {}
