package dev.tripswitch.admin;

/** Thrown when the request conflicts with existing state (409). */
public class ConflictException extends ApiException {
    public ConflictException(String code, String message, String requestId, byte[] body) {
        super(409, code, message, requestId, body, null);
    }
}
