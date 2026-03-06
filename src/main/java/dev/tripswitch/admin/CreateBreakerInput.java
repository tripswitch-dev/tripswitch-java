package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** Input for creating a breaker. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateBreakerInput(
        @JsonProperty("name") String name,
        @JsonProperty("metric") String metric,
        @JsonProperty("kind") BreakerKind kind,
        @JsonProperty("kind_params") Map<String, Object> kindParams,
        @JsonProperty("op") BreakerOp op,
        @JsonProperty("threshold") double threshold,
        @JsonProperty("window_ms") Integer windowMs,
        @JsonProperty("min_count") Integer minCount,
        @JsonProperty("min_state_duration_ms") Integer minStateDurationMs,
        @JsonProperty("cooldown_ms") Integer cooldownMs,
        @JsonProperty("eval_interval_ms") Integer evalIntervalMs,
        @JsonProperty("half_open_backoff_enabled") Boolean halfOpenBackoffEnabled,
        @JsonProperty("half_open_backoff_cap_ms") Integer halfOpenBackoffCapMs,
        @JsonProperty("half_open_indeterminate_policy") HalfOpenPolicy halfOpenIndeterminatePolicy,
        @JsonProperty("recovery_allow_rate_ramp_steps") Integer recoveryAllowRateRampSteps,
        @JsonProperty("actions") Map<String, Object> actions,
        @JsonProperty("metadata") Map<String, String> metadata
) {
    /** Creates an input with only required fields. */
    public CreateBreakerInput(String name, String metric, BreakerKind kind, BreakerOp op, double threshold) {
        this(name, metric, kind, null, op, threshold, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static Builder builder(String name, String metric, BreakerKind kind, BreakerOp op, double threshold) {
        return new Builder(name, metric, kind, op, threshold);
    }

    public static class Builder {
        private final String name;
        private final String metric;
        private final BreakerKind kind;
        private final BreakerOp op;
        private final double threshold;
        private Map<String, Object> kindParams;
        private Integer windowMs;
        private Integer minCount;
        private Integer minStateDurationMs;
        private Integer cooldownMs;
        private Integer evalIntervalMs;
        private Boolean halfOpenBackoffEnabled;
        private Integer halfOpenBackoffCapMs;
        private HalfOpenPolicy halfOpenIndeterminatePolicy;
        private Integer recoveryAllowRateRampSteps;
        private Map<String, Object> actions;
        private Map<String, String> metadata;

        Builder(String name, String metric, BreakerKind kind, BreakerOp op, double threshold) {
            this.name = name;
            this.metric = metric;
            this.kind = kind;
            this.op = op;
            this.threshold = threshold;
        }

        public Builder kindParams(Map<String, Object> v) { this.kindParams = v; return this; }
        public Builder windowMs(int v) { this.windowMs = v; return this; }
        public Builder minCount(int v) { this.minCount = v; return this; }
        public Builder minStateDurationMs(int v) { this.minStateDurationMs = v; return this; }
        public Builder cooldownMs(int v) { this.cooldownMs = v; return this; }
        public Builder evalIntervalMs(int v) { this.evalIntervalMs = v; return this; }
        public Builder halfOpenBackoffEnabled(boolean v) { this.halfOpenBackoffEnabled = v; return this; }
        public Builder halfOpenBackoffCapMs(int v) { this.halfOpenBackoffCapMs = v; return this; }
        public Builder halfOpenIndeterminatePolicy(HalfOpenPolicy v) { this.halfOpenIndeterminatePolicy = v; return this; }
        public Builder recoveryAllowRateRampSteps(int v) { this.recoveryAllowRateRampSteps = v; return this; }
        public Builder actions(Map<String, Object> v) { this.actions = v; return this; }
        public Builder metadata(Map<String, String> v) { this.metadata = v; return this; }

        public CreateBreakerInput build() {
            return new CreateBreakerInput(name, metric, kind, kindParams, op, threshold,
                    windowMs, minCount, minStateDurationMs, cooldownMs, evalIntervalMs,
                    halfOpenBackoffEnabled, halfOpenBackoffCapMs, halfOpenIndeterminatePolicy,
                    recoveryAllowRateRampSteps, actions, metadata);
        }
    }
}
