package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Response from listing events. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ListEventsResponse(
        @JsonProperty("events") List<Event> events,
        @JsonProperty("returned") int returned,
        @JsonProperty("next_cursor") String nextCursor
) {}
