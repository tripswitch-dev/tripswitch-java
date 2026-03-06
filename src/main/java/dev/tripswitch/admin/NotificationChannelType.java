package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonValue;

/** Type of notification channel. */
public enum NotificationChannelType {
    SLACK("slack"),
    PAGERDUTY("pagerduty"),
    EMAIL("email"),
    WEBHOOK("webhook");

    private final String value;

    NotificationChannelType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
