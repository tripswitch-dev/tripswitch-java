package dev.tripswitch;

/**
 * Thrown when mutually exclusive execute options are used together.
 */
public class ConflictingOptionsException extends TripSwitchException {

    public ConflictingOptionsException(String message) {
        super("tripswitch: conflicting execute options: " + message);
    }
}
