package dev.tripswitch.admin;

/** Thrown when the requested resource is not found (404). */
public class NotFoundException extends ApiException {
    public NotFoundException(String code, String message, String requestId, byte[] body) {
        super(404, code, message, requestId, body, null);
    }
}
