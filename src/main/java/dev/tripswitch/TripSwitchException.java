package dev.tripswitch;

/**
 * Base exception for all Tripswitch SDK errors.
 */
public class TripSwitchException extends RuntimeException {

    public TripSwitchException(String message) {
        super(message);
    }

    public TripSwitchException(String message, Throwable cause) {
        super(message, cause);
    }
}
