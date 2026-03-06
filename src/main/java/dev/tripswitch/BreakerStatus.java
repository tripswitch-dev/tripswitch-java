package dev.tripswitch;

/**
 * Cached state of a circuit breaker.
 * Returned by {@link TripSwitch#getState} and {@link TripSwitch#getAllStates}.
 *
 * @param name      the breaker name
 * @param state     "open", "closed", or "half_open"
 * @param allowRate 0.0 to 1.0
 */
public record BreakerStatus(String name, String state, double allowRate) {}
