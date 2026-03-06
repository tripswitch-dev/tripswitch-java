package dev.tripswitch;

import java.util.Map;

/**
 * Input for fire-and-forget sample reporting via {@link TripSwitch#report}.
 */
public final class ReportInput {

    private final String routerId;
    private final String metric;
    private final double value;
    private final boolean ok;
    private final String traceId;
    private final Map<String, String> tags;

    private ReportInput(String routerId, String metric, double value, boolean ok, String traceId, Map<String, String> tags) {
        this.routerId = routerId;
        this.metric = metric;
        this.value = value;
        this.ok = ok;
        this.traceId = traceId;
        this.tags = tags;
    }

    public String getRouterId() { return routerId; }
    public String getMetric() { return metric; }
    public double getValue() { return value; }
    public boolean isOk() { return ok; }
    public String getTraceId() { return traceId; }
    public Map<String, String> getTags() { return tags; }

    public static Builder builder(String routerId, String metric) {
        return new Builder(routerId, metric);
    }

    public static class Builder {
        private final String routerId;
        private final String metric;
        private double value;
        private boolean ok = true;
        private String traceId;
        private Map<String, String> tags;

        Builder(String routerId, String metric) { this.routerId = routerId; this.metric = metric; }
        public Builder value(double v) { this.value = v; return this; }
        public Builder ok(boolean ok) { this.ok = ok; return this; }
        public Builder traceId(String id) { this.traceId = id; return this; }
        public Builder tags(Map<String, String> tags) { this.tags = tags; return this; }

        public ReportInput build() {
            return new ReportInput(routerId, metric, value, ok, traceId, tags);
        }
    }
}
