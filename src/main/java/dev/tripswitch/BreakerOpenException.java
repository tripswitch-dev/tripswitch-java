package dev.tripswitch;

/**
 * Thrown when a circuit breaker is open or a half-open request is rejected.
 */
public class BreakerOpenException extends TripSwitchException {

    private final String breakerName;

    public BreakerOpenException(String breakerName) {
        super("tripswitch: breaker is open: " + breakerName);
        this.breakerName = breakerName;
    }

    public BreakerOpenException() {
        super("tripswitch: breaker is open");
        this.breakerName = null;
    }

    /** Returns the name of the breaker that is open, or null if unknown. */
    public String getBreakerName() {
        return breakerName;
    }
}
