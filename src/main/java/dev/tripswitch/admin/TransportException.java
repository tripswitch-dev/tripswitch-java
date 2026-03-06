package dev.tripswitch.admin;

import dev.tripswitch.TripSwitchException;

/** Thrown when a network or I/O failure occurs. */
public class TransportException extends TripSwitchException {
    public TransportException(String message) {
        super("tripswitch: transport failure: " + message);
    }

    public TransportException(String message, Throwable cause) {
        super("tripswitch: transport failure: " + message, cause);
    }
}
