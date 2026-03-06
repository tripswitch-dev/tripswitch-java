package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonValue;

/** Event types that trigger notifications. */
public enum NotificationEventType {
    TRIP("trip"),
    RECOVER("recover");

    private final String value;

    NotificationEventType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
