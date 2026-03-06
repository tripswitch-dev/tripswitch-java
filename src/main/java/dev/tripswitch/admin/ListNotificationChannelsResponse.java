package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Response from listing notification channels. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ListNotificationChannelsResponse(
        @JsonProperty("channels") List<NotificationChannel> channels,
        @JsonProperty("next_cursor") String nextCursor
) {}
