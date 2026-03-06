package dev.tripswitch.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** A circuit breaker configuration. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Breaker(
        @JsonProperty("id") String id,
        @JsonProperty("router_id") String routerId,
        @JsonProperty("name") String name,
        @JsonProperty("metric") String metric,
        @JsonProperty("kind") BreakerKind kind,
        @JsonProperty("kind_params") Map<String, Object> kindParams,
        @JsonProperty("op") BreakerOp op,
        @JsonProperty("threshold") double threshold,
        @JsonProperty("window_ms") int windowMs,
        @JsonProperty("min_count") int minCount,
        @JsonProperty("min_state_duration_ms") int minStateDurationMs,
        @JsonProperty("cooldown_ms") int cooldownMs,
        @JsonProperty("eval_interval_ms") int evalIntervalMs,
        @JsonProperty("half_open_confirmation_ms") int halfOpenConfirmationMs,
        @JsonProperty("half_open_backoff_enabled") boolean halfOpenBackoffEnabled,
        @JsonProperty("half_open_backoff_cap_ms") int halfOpenBackoffCapMs,
        @JsonProperty("half_open_indeterminate_policy") HalfOpenPolicy halfOpenIndeterminatePolicy,
        @JsonProperty("recovery_window_ms") int recoveryWindowMs,
        @JsonProperty("recovery_allow_rate_ramp_steps") int recoveryAllowRateRampSteps,
        @JsonProperty("actions") Map<String, Object> actions,
        @JsonProperty("metadata") Map<String, String> metadata
) {}
