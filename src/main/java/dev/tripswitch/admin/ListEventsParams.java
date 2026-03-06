package dev.tripswitch.admin;

import java.time.Instant;

/** Parameters for listing events. */
public record ListEventsParams(
        String breakerId,
        Instant startTime,
        Instant endTime,
        String cursor,
        int limit
) {
    public ListEventsParams() {
        this(null, null, null, null, 0);
    }

    public ListEventsParams(int limit) {
        this(null, null, null, null, limit);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String breakerId;
        private Instant startTime;
        private Instant endTime;
        private String cursor;
        private int limit;

        public Builder breakerId(String v) { this.breakerId = v; return this; }
        public Builder startTime(Instant v) { this.startTime = v; return this; }
        public Builder endTime(Instant v) { this.endTime = v; return this; }
        public Builder cursor(String v) { this.cursor = v; return this; }
        public Builder limit(int v) { this.limit = v; return this; }

        public ListEventsParams build() {
            return new ListEventsParams(breakerId, startTime, endTime, cursor, limit);
        }
    }
}
