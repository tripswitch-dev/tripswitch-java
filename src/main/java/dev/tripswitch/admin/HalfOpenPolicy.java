package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonValue;

/** Policy for half-open state with insufficient data. */
public enum HalfOpenPolicy {
    OPTIMISTIC("optimistic"),
    CONSERVATIVE("conservative"),
    PESSIMISTIC("pessimistic");

    private final String value;

    HalfOpenPolicy(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
