package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonValue;

/** Aggregation type for a breaker. */
public enum BreakerKind {
    ERROR_RATE("error_rate"),
    AVG("avg"),
    P95("p95"),
    MAX("max"),
    MIN("min"),
    SUM("sum"),
    STDDEV("stddev"),
    COUNT("count"),
    PERCENTILE("percentile"),
    CONSECUTIVE_FAILURES("consecutive_failures"),
    DELTA("delta");

    private final String value;

    BreakerKind(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
