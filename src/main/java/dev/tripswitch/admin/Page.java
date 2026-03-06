package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Generic pagination wrapper. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Page<T>(
        @JsonProperty("items") List<T> items,
        @JsonProperty("next_cursor") String nextCursor
) {}
