package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Input for creating a notification channel. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateNotificationChannelInput(
        @JsonProperty("name") String name,
        @JsonProperty("channel") NotificationChannelType channel,
        @JsonProperty("config") Map<String, Object> config,
        @JsonProperty("events") List<NotificationEventType> events,
        @JsonProperty("enabled") Boolean enabled
) {}
