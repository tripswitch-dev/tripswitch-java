package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonValue;

/** Comparison operator for a breaker threshold. */
public enum BreakerOp {
    GT("gt"),
    LT("lt"),
    GTE("gte"),
    LTE("lte");

    private final String value;

    BreakerOp(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
