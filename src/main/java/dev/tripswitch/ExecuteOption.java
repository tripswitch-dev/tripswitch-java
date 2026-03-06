package dev.tripswitch;

/**
 * Functional interface for configuring a single {@link TripSwitch#execute} call.
 * Use the static factory methods on {@link TripSwitch} to create options.
 */
@FunctionalInterface
public interface ExecuteOption {
    void apply(ExecuteOptions opts);
}
