package dev.tripswitch;

/**
 * Thrown when a selector is used but the metadata cache is empty.
 */
public class MetadataUnavailableException extends TripSwitchException {

    public MetadataUnavailableException() {
        super("tripswitch: metadata cache unavailable");
    }
}
