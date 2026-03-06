package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A notification channel configuration. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationChannel(
        @JsonProperty("id") String id,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("name") String name,
        @JsonProperty("channel") NotificationChannelType channel,
        @JsonProperty("config") Map<String, Object> config,
        @JsonProperty("events") List<NotificationEventType> events,
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {}
