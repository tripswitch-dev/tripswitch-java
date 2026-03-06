package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Input for updating a notification channel. All fields are optional. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateNotificationChannelInput(
        @JsonProperty("name") String name,
        @JsonProperty("config") Map<String, Object> config,
        @JsonProperty("events") List<NotificationEventType> events,
        @JsonProperty("enabled") Boolean enabled
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private Map<String, Object> config;
        private List<NotificationEventType> events;
        private Boolean enabled;

        public Builder name(String v) { this.name = v; return this; }
        public Builder config(Map<String, Object> v) { this.config = v; return this; }
        public Builder events(List<NotificationEventType> v) { this.events = v; return this; }
        public Builder enabled(boolean v) { this.enabled = v; return this; }

        public UpdateNotificationChannelInput build() {
            return new UpdateNotificationChannelInput(name, config, events, enabled);
        }
    }
}
