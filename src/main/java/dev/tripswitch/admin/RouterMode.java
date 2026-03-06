package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonValue;

/** Routing mode for a router. */
public enum RouterMode {
    STATIC("static"),
    CANARY("canary"),
    WEIGHTED("weighted");

    private final String value;

    RouterMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
